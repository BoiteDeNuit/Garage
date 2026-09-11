package com.example.dto;

import jakarta.validation.constraints.*;

public record CarDto(
        Long id,
        @NotBlank(message = "Бренд не должен быть пустым")
        @Size(max = 50, message = "Бренд не длиннее 50 символов")
        String brand,
        @NotBlank(message = "Модель обязательна")
        String model,
        String engineCode,
        @NotNull(message = "Мощность обязательна")
        @Min(value = 1,message = "Мощность должна быть положительной")
        @Max(value = 3000,message = "Мощность неправдоподобно велика")
        Integer horsePower,
        @NotNull(message = "Год обязателен")
        @Min(1885)@Max(2100)
        Integer year
) {}
