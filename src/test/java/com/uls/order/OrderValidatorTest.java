package com.uls.order;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    private Exchange exchangeOf(String body) {
        Exchange e = new DefaultExchange(new DefaultCamelContext());
        e.getIn().setHeader(Exchange.FILE_NAME, "order.txt");
        e.getIn().setBody(body);
        return e;
    }

    @Test
    void validOrder_passes() {
        assertDoesNotThrow(() -> validator.process(exchangeOf("Ali,Book,2,50000")));
    }

    @Test
    void emptyFile_isRejected() {
        assertThrows(InvalidOrderException.class, () -> validator.process(exchangeOf("")));
    }

    @Test
    void whitespaceOnly_isRejected() {
        assertThrows(InvalidOrderException.class, () -> validator.process(exchangeOf("   ")));
    }

    @Test
    void tooFewFields_isRejected() {
        assertThrows(InvalidOrderException.class, () -> validator.process(exchangeOf("Ali,Book")));
    }

    @Test
    void emptyFieldInside_isRejected() {
        assertThrows(InvalidOrderException.class, () -> validator.process(exchangeOf("Ali,Book,,50000")));
    }
}
