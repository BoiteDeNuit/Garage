package com.example.exception;

// Конфликт с состоянием фото, отдаётся как 409
public class PhotoStateException extends RuntimeException {
    private PhotoStateException(String message)
    {
        super(message);
    }
    public static PhotoStateException notUploaded()
    {
        return new PhotoStateException("Файл ещё не загружен в хранилище");
    }
}
