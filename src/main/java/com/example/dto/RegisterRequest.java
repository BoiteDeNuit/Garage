package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Роли тут нет специально: даже если прислать "role":"ADMIN", её некуда положить
public record RegisterRequest(
        @Schema(example = "seller_42")
        @NotBlank(message = "Логин не должен быть пустым")
        @Pattern(regexp = "^[a-z0-9_]{3,32}$", message = "Логин: 3–32 символа, строчные латинские буквы, цифры и _")
        String username,
        @Schema(example = "correct-horse-battery")
        @NotBlank(message = "Пароль не должен быть пустым")
        @Size(min = 8, max = 72, message = "Пароль от 8 до 72 символов")
        String password) {
}
