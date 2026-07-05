package com.uls.order;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class OrderTransformer {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String addTimestamp(String body) {
        String now = LocalDateTime.now().format(FORMAT);
        return body.trim() + "," + now + System.lineSeparator();
    }
}
