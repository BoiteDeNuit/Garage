package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// Тип и размер клиент заявляет до загрузки: они войдут в подпись ссылки, другой файл хранилище не примет
public record PhotoUploadRequest(
        @Schema(example = "image/jpeg")
        @NotNull(message = "Тип фото обязателен")
        @Pattern(regexp = "image/(jpeg|png|webp)", message = "Фото только JPEG, PNG или WebP")
        String contentType,
        @Schema(description = "Размер файла в байтах, не больше 10 МБ", example = "2400000")
        @NotNull(message = "Размер фото обязателен")
        @Positive(message = "Размер фото больше нуля")
        @Max(value = PhotoUploadRequest.MAX_BYTES, message = "Фото не больше 10 МБ")
        Long sizeBytes) {
    public static final long MAX_BYTES = 10 * 1024 * 1024;
}
