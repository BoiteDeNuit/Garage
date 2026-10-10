package com.example.exception;

// Нет куки, токен неизвестен, истёк или погашен. Причину наружу не называем: ответ один, 401
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException()
    {
        super("Сессия истекла, войдите заново");
    }
}
