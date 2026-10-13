package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @Schema(description = "Её увидит продавец", example = "На фото другая машина")
        @NotBlank(message = "Причина отказа обязательна")
        @Size(min = 5, max = 500, message = "Причина отказа от 5 до 500 символов")
        String reason) {
}
