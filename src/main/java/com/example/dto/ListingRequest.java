package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ListingRequest(
        @Schema(example = "Toyota")
        @NotBlank(message = "Марка не должна быть пустой")
        @Size(max = 50, message = "Марка не длиннее 50 символов")
        String brand,
        @Schema(example = "Supra")
        @NotBlank(message = "Модель обязательна")
        @Size(max = 100, message = "Модель не длиннее 100 символов")
        String model,
        @Schema(example = "2JZ")
        @Size(max = 50, message = "Код двигателя не длиннее 50 символов")
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
        @Schema(example = "154000")
        @NotNull(message = "Пробег обязателен")
        @PositiveOrZero(message = "Пробег не может быть отрицательным")
        @Max(value = 2_000_000,message = "Пробег неправдоподобно велик")
        Integer mileageKm,
        @Schema(example = "4500000", description = "Для черновика можно не указывать")
        @Positive(message = "Цена должна быть больше нуля")
        @Digits(integer = 10, fraction = 2, message = "Цена: не больше 10 знаков до запятой и 2 после")
        BigDecimal price,
        @Schema(example = "Самара")
        @Size(max = 100, message = "Город не длиннее 100 символов")
        String city,
        @Schema(example = "Один владелец, не бита")
        @Size(max = 2000, message = "Описание не длиннее 2000 символов")
        String description
) {}
