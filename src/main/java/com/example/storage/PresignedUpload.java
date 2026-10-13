package com.example.storage;

import java.net.URL;
import java.time.Instant;
import java.util.Map;

// Ссылка на загрузку и заголовки, которые клиент обязан прислать ровно такими: они вошли в подпись
public record PresignedUpload(URL url, Map<String, String> headers, Instant expiresAt) {
}
