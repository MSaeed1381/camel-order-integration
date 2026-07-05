package com.uls.order;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;


public class OrderValidator implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        String body = exchange.getIn().getBody(String.class);

        String[] fields = getFields(body, fileName);

        for (String f : fields) {
            if (f == null || f.trim().isEmpty()) {
                throw new InvalidOrderException("One of the fields is empty: " + fileName);
            }
        }
    }

    private static String[] getFields(String body, String fileName) throws InvalidOrderException {
        if (body == null || body.trim().isEmpty()) {
            throw new InvalidOrderException("File is empty: " + fileName);
        }

        String firstLine = body.trim().split("\\R")[0];
        String[] fields = firstLine.split(",");

        if (fields.length < 4) {
            throw new InvalidOrderException(
                    "Incomplete data (expected 4 fields but found " + fields.length
                            + "): " + fileName);
        }
        return fields;
    }
}
