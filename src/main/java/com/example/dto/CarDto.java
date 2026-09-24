package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CarDto(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @Schema(example = "Toyota")
        @NotBlank(message = "Бренд не должен быть пустым")
        @Size(max = 50, message = "Бренд не длиннее 50 символов")
        String brand,
        @Schema(example = "Supra")
        @NotBlank(message = "Модель обязательна")
        String model,
        @Schema(example = "2JZ")
        @NotNull(message = "Двигатель обязателен")
        String engineCode,
        @Schema(example = "320")
        @NotNull(message = "Мощность обязательна")
        @Min(value = 1,message = "Мощность должна быть положительной")
        @Max(value = 3000,message = "Мощность неправдоподобно велика")
        Integer horsePower,
        @Schema(example = "1998")
        @NotNull(message = "Год обязателен")
        @Min(1885)@Max(2100)
        Integer year,
        @Schema(example = "4500000")
        @PositiveOrZero(message = "Цена не может быть отрицательной")
        BigDecimal price
) {}
