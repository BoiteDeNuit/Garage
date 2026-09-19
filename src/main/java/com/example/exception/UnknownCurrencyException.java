package com.example.exception;

public class UnknownCurrencyException extends RuntimeException {
    public UnknownCurrencyException(String message) {
        super(message);
    }
}
