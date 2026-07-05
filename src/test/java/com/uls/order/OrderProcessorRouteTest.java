package com.uls.order;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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

    private void mockFileEndpoints() throws Exception {
        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out").replace().to("mock:out");
        });
        AdviceWith.adviceWith(context, "audit-tap", a ->
                a.weaveByToUri("file:data/audit").replace().to("mock:audit"));
        AdviceWith.adviceWith(context, "temp-recovery", a ->
                a.replaceFromWith("direct:tempRecovery"));
    }

    private void send(String body, String fileName) {
        template.sendBodyAndHeader("direct:start", body, Exchange.FILE_NAME, fileName);
    }

    @Test
    void validOrderGetsTimestampAppended() throws Exception {
        mockFileEndpoints();
        context.start();

        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);

        send(VALID_ORDER, "order.txt");

        out.assertIsSatisfied();
        String body = out.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.startsWith(VALID_ORDER + ","));
    }

    @Test
    void validOrderIsArchivedAsRawCopy() throws Exception {
        mockFileEndpoints();
        context.start();

        MockEndpoint audit = getMockEndpoint("mock:audit");
        audit.expectedBodiesReceived(VALID_ORDER);

        send(VALID_ORDER, "order.txt");

        audit.assertIsSatisfied();
    }

    @Test
    void incompleteOrderIsRejected() throws Exception {
        mockFileEndpoints();
        context.start();

        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(0);

        CamelExecutionException ex =
                assertThrows(CamelExecutionException.class, () -> send("Ali,Laptop", "bad.txt"));
        assertInstanceOf(InvalidOrderException.class, ex.getCause());

        out.assertIsSatisfied();
    }

    @Test
    void emptyOrderIsRejected() throws Exception {
        mockFileEndpoints();
        context.start();

        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(0);

        CamelExecutionException ex =
                assertThrows(CamelExecutionException.class, () -> send("   ", "empty.txt"));
        assertInstanceOf(InvalidOrderException.class, ex.getCause());

        out.assertIsSatisfied();
    }

    @Test
    void unavailableOutputIsBufferedToTemp() throws Exception {
        Stats.buffered.set(0);

        AdviceWith.adviceWith(context, "order-processor", a -> {
            a.replaceFromWith("direct:start");
            a.weaveByToUri("file:data/out").replace().process(e -> {
                throw new IOException("output folder unavailable");
            });
        });
        AdviceWith.adviceWith(context, "audit-tap", a ->
                a.weaveByToUri("file:data/audit").replace().to("mock:audit"));
        AdviceWith.adviceWith(context, "temp-recovery", a ->
                a.replaceFromWith("direct:tempRecovery"));
        context.start();

        send(VALID_ORDER, "order.txt");

        assertEquals(1, Stats.buffered.get());
    }
}
