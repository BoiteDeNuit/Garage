package com.example.dto;

import java.time.Instant;
import java.util.Map;

// Куда и как грузить файл. Заголовки отправить ровно такими: они вошли в подпись
public record PhotoUploadDto(Long photoId, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {
}
