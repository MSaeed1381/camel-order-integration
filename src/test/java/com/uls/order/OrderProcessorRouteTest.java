package com.uls.order;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderProcessorRouteTest extends CamelTestSupport {

    private static final String VALID_ORDER = "Ali,Laptop,1,1000";

    OrderProcessorRouteTest() {
        testConfiguration().withUseAdviceWith(true);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new OrderProcessorRoute();
    }

    @BeforeEach
    void resetStats() {
        Stats.processed.set(0);
        Stats.failed.set(0);
        Stats.buffered.set(0);
    }

    private void silenceOtherFileRoute(String routeUnderTest) throws Exception {
        if (!"order-processor".equals(routeUnderTest)) {
            AdviceWith.adviceWith(context, "order-processor", a -> a.replaceFromWith("direct:unusedIn"));
        }
        if (!"temp-recovery".equals(routeUnderTest)) {
            AdviceWith.adviceWith(context, "temp-recovery", a -> a.replaceFromWith("direct:unusedTemp"));
        }
        AdviceWith.adviceWith(context, "audit-tap",
                a -> a.weaveByToUri("file:data/audit*").replace().to("mock:audit"));
    }

    private void send(String body, String fileName) {
        template.sendBodyAndHeader("direct:start", body, Exchange.FILE_NAME, fileName);
    }

    @Test
    void validOrderGetsTimestampAppended() throws Exception {
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out*").replace().to("mock:out");
        });
        silenceOtherFileRoute("order-processor");
        context.start();

        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);

        send(VALID_ORDER, "order.txt");

        out.assertIsSatisfied();
        String body = out.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.startsWith(VALID_ORDER + ","));
        assertEquals(1, Stats.processed.get());
    }

    @Test
    void validOrderIsArchivedAsRawCopy() throws Exception {
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out*").replace().to("mock:out");
        });
        silenceOtherFileRoute("order-processor");
        context.start();

        MockEndpoint audit = getMockEndpoint("mock:audit");
        audit.expectedBodiesReceived(VALID_ORDER);

        send(VALID_ORDER, "order.txt");

        audit.assertIsSatisfied();
    }

    @Test
    void incompleteOrderIsRejected() throws Exception {
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out*").replace().to("mock:out");
        });
        silenceOtherFileRoute("order-processor");
        context.start();

        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(0);

        CamelExecutionException ex =
                assertThrows(CamelExecutionException.class, () -> send("Ali,Laptop", "bad.txt"));
        assertInstanceOf(InvalidOrderException.class, ex.getCause());

        out.assertIsSatisfied();
        assertEquals(1, Stats.failed.get());
        assertEquals(0, Stats.processed.get());
    }

    @Test
    void emptyOrderIsRejected() throws Exception {
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out*").replace().to("mock:out");
        });
        silenceOtherFileRoute("order-processor");
        context.start();

        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(0);

        CamelExecutionException ex =
                assertThrows(CamelExecutionException.class, () -> send("   ", "empty.txt"));
        assertInstanceOf(InvalidOrderException.class, ex.getCause());

        out.assertIsSatisfied();
        assertEquals(1, Stats.failed.get());
    }

    @Test
    void transientErrorIsRetriedThreeTimesThenFails() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out*").replace().process(e -> {
                attempts.incrementAndGet();
                throw new RuntimeException("transient boom");
            });
        });
        silenceOtherFileRoute("order-processor");
        context.start();

        assertThrows(CamelExecutionException.class, () -> send(VALID_ORDER, "t.txt"));

        assertEquals(4, attempts.get());
        assertEquals(0, Stats.processed.get());
    }

    @Test
    void unavailableOutputIsBufferedToTemp() throws Exception {
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out*").replace()
                    .process(e -> { throw new GenericFileOperationFailedException("output down"); });
        });
        silenceOtherFileRoute("order-processor");
        context.start();

        send(VALID_ORDER, "order.txt");

        assertEquals(1, Stats.buffered.get());
        assertEquals(0, Stats.processed.get());
    }

    @Test
    void recoverySuccessMovesBufferedToOutput() throws Exception {
        AdviceWith.adviceWith(context, "temp-recovery", a -> {
            a.replaceFromWith("direct:recover");
            a.weaveByToUri("file:data/out*").replace().to("mock:out");
        });
        silenceOtherFileRoute("temp-recovery");
        context.start();

        getMockEndpoint("mock:out").expectedMessageCount(1);
        Stats.buffered.set(1);

        template.sendBodyAndHeader("direct:recover", VALID_ORDER, Exchange.FILE_NAME, "buf.txt");

        getMockEndpoint("mock:out").assertIsSatisfied();
        assertEquals(0, Stats.buffered.get());
        assertEquals(1, Stats.processed.get());
    }

    @Test
    void recoveryFailureKeepsFileAndDoesNotDoubleCount() throws Exception {
        AdviceWith.adviceWith(context, "temp-recovery", a -> {
            a.replaceFromWith("direct:recover");
            a.weaveByToUri("file:data/out*").replace()
                    .process(e -> { throw new GenericFileOperationFailedException("still down"); });
        });
        silenceOtherFileRoute("temp-recovery");
        context.start();

        Stats.buffered.set(1);

        assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:recover", VALID_ORDER, Exchange.FILE_NAME, "buf.txt"));

        assertEquals(1, Stats.buffered.get());
        assertEquals(0, Stats.processed.get());
    }
}
