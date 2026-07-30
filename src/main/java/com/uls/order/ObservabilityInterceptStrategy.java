package com.uls.order;

import java.util.UUID;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObservabilityInterceptStrategy implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityInterceptStrategy.class);

    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final long SLOW_STEP_MS = 500;

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext context, NamedNode node,
                                                  Processor target, Processor next) {
        if (target instanceof ObservedProcessor) {
            return target;
        }
        return new ObservedProcessor(node.getId(), node.getShortName(), target);
    }

    private static final class ObservedProcessor extends DelegateAsyncProcessor {

        private final String nodeId;
        private final String action;

        private ObservedProcessor(String nodeId, String action, Processor target) {
            super(target);
            this.nodeId = nodeId;
            this.action = action;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            String routeId = exchange.getFromRouteId();
            if ("status-report".equals(routeId)) {
                return super.process(exchange, callback);
            }

            ensureCorrelationId(exchange);
            long started = System.nanoTime();

            return super.process(exchange, doneSync -> {
                try {
                    long latencyMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
                    String status = exchange.getException() != null ? "failed" : "success";
                    String corrId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
                    if (latencyMs > SLOW_STEP_MS) {
                        LOG.warn("[SLOW] corrId={} route={} action={} node={} file={} latencyMs={} status={}",
                                corrId, routeId, action, nodeId, fileName(exchange), latencyMs, status);
                    } else {
                        LOG.info("corrId={} route={} action={} node={} file={} latencyMs={} status={}",
                                corrId, routeId, action, nodeId, fileName(exchange), latencyMs, status);
                    }
                } finally {
                    callback.done(doneSync);
                }
            });
        }
    }

    private static void ensureCorrelationId(Exchange exchange) {
        if (exchange.getIn().getHeader(CORRELATION_ID, String.class) == null) {
            exchange.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString().substring(0, 8));
        }
    }

    private static String fileName(Exchange exchange) {
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        return fileName == null ? "-" : fileName;
    }
}
