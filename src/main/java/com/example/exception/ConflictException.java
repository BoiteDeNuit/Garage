package com.example.exception;

// Запрос правильный, но противоречит тому, что уже есть: превышен предел. Отдаётся как 409
public class ConflictException extends RuntimeException {
    public ConflictException(String message)
    {
        super(message);
    }
}
