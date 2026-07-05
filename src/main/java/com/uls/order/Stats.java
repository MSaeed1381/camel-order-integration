package com.uls.order;

import java.util.concurrent.atomic.AtomicLong;


public class Stats {
    public static final AtomicLong processed = new AtomicLong(0);
    public static final AtomicLong failed = new AtomicLong(0);
    public static final AtomicLong buffered = new AtomicLong(0);
}
