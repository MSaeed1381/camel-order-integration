package com.uls.order;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;


public class OrderProcessorRoute extends RouteBuilder {

    @Override
    public void configure() {
        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(LoggingLevel.OFF)
                .logRetryAttempted(false)
                .logExhausted(false)
                .logStackTrace(false));

        onException(InvalidOrderException.class)
                .maximumRedeliveries(0)
                .handled(false)
                .logRetryAttempted(false)
                .logExhausted(false)
                .logStackTrace(false)
                .process(e -> Stats.failed.incrementAndGet());

        onException(GenericFileOperationFailedException.class)
                .handled(true)
                .maximumRedeliveries(0)
                .process(e -> Stats.buffered.incrementAndGet())
                .to("file:data/temp");

        from("file:data/in?moveFailed=../error&move=.done&delay=3000")
                .routeId("order-processor")
                .convertBodyTo(String.class)
                .wireTap("direct:audit")
                .process(new OrderValidator())
                .bean(OrderTransformer.class, "addTimestamp")
                .to("file:data/out")
                .process(e -> Stats.processed.incrementAndGet());

        from("file:data/temp?delay=5000&delete=true")
                .routeId("temp-recovery")
                .onException(GenericFileOperationFailedException.class)
                    .maximumRedeliveries(0)
                    .handled(false)
                    .logExhausted(false)
                    .logStackTrace(false)
                .end()
                .to("file:data/out")
                .process(e -> {
                    Stats.buffered.decrementAndGet();
                    Stats.processed.incrementAndGet();
                });

        from("direct:audit")
                .routeId("audit-tap")
                .to("file:data/audit");
    }
}
