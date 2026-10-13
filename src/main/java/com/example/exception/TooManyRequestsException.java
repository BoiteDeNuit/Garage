package com.example.exception;

// Превышен предел частоты. retryAfterSeconds — через сколько откроется следующее окно, уходит в заголовок Retry-After
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;
    public TooManyRequestsException(String message, long retryAfterSeconds)
    {
        super(message);
        this.retryAfterSeconds=retryAfterSeconds;
    }
    public long getRetryAfterSeconds()
    {
        return retryAfterSeconds;
    }
}
