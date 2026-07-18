package org.example.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounter {
    private final AtomicInteger value = new AtomicInteger();
    public void increment()
    {
        value.incrementAndGet();
    }
    public int getValue()
    {
        return value.get();
    }
}
