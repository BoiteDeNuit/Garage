    package com.example.dto;

    import jakarta.validation.constraints.NotBlank;

    public record LoginRequest(
            @NotBlank(message = "Имя пользователя не должно быть пустым")
            String username,
            @NotBlank(message = "Пароль не должен быть пустым")
            String password) {
    }
