package com.example.dto;

import com.example.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(
        @Schema(description = "USER или MODERATOR", example = "MODERATOR")
        @NotNull(message = "Роль обязательна")
        Role role) {
}
