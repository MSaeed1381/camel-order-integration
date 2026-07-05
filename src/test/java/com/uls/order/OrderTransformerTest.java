package com.uls.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTransformerTest {

    @Test
    void appendsTimestampAsFifthCsvFieldOnSameLine() {
        String result = new OrderTransformer().addTimestamp("Ali,Laptop,1,1000").trim();

        assertTrue(result.startsWith("Ali,Laptop,1,1000,"));
        assertEquals(5, result.split(",").length);
        assertEquals(1, result.split("\\R").length);
    }
}
