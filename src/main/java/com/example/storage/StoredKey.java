package com.example.storage;

import java.time.Instant;

// Файл из листинга бакета: ключ и когда его положили
public record StoredKey(String key, Instant lastModified) {
}
