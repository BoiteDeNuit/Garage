package com.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SavedSearchRequest(
        @Schema(example = "Супра до пяти миллионов")
        @NotBlank(message = "Название поиска обязательно")
        @Size(max = 100, message = "Название поиска не длиннее 100 символов")
        String name,
        @Schema(description = "Те же поля, что у фильтров ленты")
        @NotNull(message = "Фильтры поиска обязательны")
        @Valid
        ListingSearchCriteria criteria) {
}
