package com.example.exception;

public class StorageFullException extends Exception{
    public StorageFullException(String message) {
        super(message);
    }
}