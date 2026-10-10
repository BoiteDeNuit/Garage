package com.example.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
    public static EntityNotFoundException listing(Long id)
    {
        return new EntityNotFoundException("Объявление с id: " + id + " не найдено");
    }
}
