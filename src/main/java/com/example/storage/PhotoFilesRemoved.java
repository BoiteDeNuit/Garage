package com.example.storage;

import java.util.List;

// Строки фото удалены в транзакции. Файлы удалит PhotoFilesCleaner, но только после коммита
public record PhotoFilesRemoved(List<String> objectKeys) {
}
