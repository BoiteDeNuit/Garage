    package com.example.dto;

    import io.swagger.v3.oas.annotations.media.Schema;
    import jakarta.validation.constraints.NotBlank;

    public record LoginRequest(
            @Schema(example = "admin")
            @NotBlank(message = "Имя пользователя не должно быть пустым")
            String username,
            @NotBlank(message = "Пароль не должен быть пустым")
            String password) {
    }
