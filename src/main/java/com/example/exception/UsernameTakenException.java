package com.example.exception;

public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException()
    {
        super("Логин уже занят");
    }
}
