package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PhotoOrderRequest(
        @Schema(description = "id всех фото объявления в новом порядке, каждое по одному разу", example = "[12, 10, 11]")
        @NotNull(message = "Нужен список id фото")
        List<@NotNull(message = "id фото не может быть пустым") Long> photoIds) {
}
