package com.example.dto;

import com.example.model.Role;

// Без хэша пароля: наружу только то, что нужно клиенту
public record UserDto(Long id, String username, Role role) {
}
