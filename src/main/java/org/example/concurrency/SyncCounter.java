package org.example.concurrency;

public class SyncCounter {
    private int value;
    public synchronized void increment()
    {
        value++;
    }
    public synchronized int getValue()
    {
        return value;
    }
}
