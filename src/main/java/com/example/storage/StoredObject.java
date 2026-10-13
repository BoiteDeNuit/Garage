package com.example.storage;

// Что хранилище знает о файле по HeadObject: сам файл не скачивается
public record StoredObject(long sizeBytes, String contentType) {
}
