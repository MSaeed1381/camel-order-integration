package com.uls.order;

import org.apache.camel.builder.RouteBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class StatusReportRoute extends RouteBuilder {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void configure() {
        from("timer:status?period=10000&delay=5000")
                .routeId("status-report")
                .process(exchange -> {
                    String now = LocalDateTime.now().format(FORMAT);
                    String threadName = Thread.currentThread().getName();
                    String report = """
                            
                            System status report - %s
                               Report thread : %s
                               Status        : running and healthy
                               Processed     : %d
                               Failed        : %d
                               Buffered(temp): %d
                            """.formatted(now, threadName,
                                    Stats.processed.get(), Stats.failed.get(), Stats.buffered.get());
                    System.out.print(report);
                });
    }
}
