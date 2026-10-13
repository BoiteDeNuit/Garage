package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Причину видит продавец: в отклонённых объявлениях она стоит причиной отказа
public record BanRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
}
