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
                .retryAttemptedLogLevel(LoggingLevel.WARN));

        onException(InvalidOrderException.class)
                .maximumRedeliveries(0)
                .handled(false)
                .log(LoggingLevel.ERROR, "Invalid file: ${exception.message} -> moved to data/error")
                .process(e -> Stats.failed.incrementAndGet());

        onException(GenericFileOperationFailedException.class)
                .handled(true)
                .maximumRedeliveries(0)
                .log(LoggingLevel.WARN, "Output folder unavailable; buffering to data/temp: ${header.CamelFileName}")
                .process(e -> Stats.buffered.incrementAndGet())
                .to("file:data/temp");

        from("file:data/in?moveFailed=../error&move=.done&delay=3000")
                .routeId("order-processor")
                .convertBodyTo(String.class)
                .log("[thread: ${threadName}] New file received: ${header.CamelFileName}")
                .wireTap("direct:audit")
                .process(new OrderValidator())
                .bean(OrderTransformer.class, "addTimestamp")
                .log("[thread: ${threadName}] Processed, writing to data/out")
                .to("file:data/out")
                .process(e -> Stats.processed.incrementAndGet());

        from("file:data/temp?delay=5000&delete=true")
                .routeId("temp-recovery")
                .onException(GenericFileOperationFailedException.class)
                    .maximumRedeliveries(0)
                    .handled(false)
                    .logExhausted(false)
                    .logStackTrace(false)
                    .log(LoggingLevel.WARN, "Output still unavailable; keeping file in data/temp: ${header.CamelFileName}")
                .end()
                .log("[thread: ${threadName}] Moving buffered file to output: ${header.CamelFileName}")
                .to("file:data/out")
                .process(e -> {
                    Stats.buffered.decrementAndGet();
                    Stats.processed.incrementAndGet();
                });

        from("direct:audit")
                .routeId("audit-tap")
                .log("[thread: ${threadName}] Archiving a copy: ${header.CamelFileName}")
                .to("file:data/audit");
    }
}
