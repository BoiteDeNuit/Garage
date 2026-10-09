package com.example.dto;

import com.example.model.BodyType;
import com.example.model.FuelType;
import com.example.model.Transmission;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Фильтры ленты из query-параметров. Все необязательные: чего нет в запросе, по тому не фильтруем.
// Границы «от» и «до» включаются
public record ListingSearchCriteria(
        @Parameter(description = "Слова из марки, модели и описания с учётом словоформ. "
                + "Фраза в кавычках, or между словами, минус перед словом исключает его", example = "небольшой пробег -дтп")
        @Size(max = 200, message = "Поисковый запрос не длиннее 200 символов")
        String q,
        @Parameter(description = "Марка без учёта регистра", example = "Toyota")
        @Size(max = 50, message = "Марка не длиннее 50 символов")
        String brand,
        @Parameter(description = "Модель без учёта регистра", example = "Supra")
        @Size(max = 100, message = "Модель не длиннее 100 символов")
        String model,
        @Parameter(example = "1990")
        @Min(value = 1885, message = "Год «от» не раньше 1885")
        @Max(value = 2100, message = "Год «от» не позже 2100")
        Integer yearFrom,
        @Parameter(example = "2005")
        @Min(value = 1885, message = "Год «до» не раньше 1885")
        @Max(value = 2100, message = "Год «до» не позже 2100")
        Integer yearTo,
        @Parameter(example = "1000000")
        @PositiveOrZero(message = "Цена «от» не может быть отрицательной")
        @Digits(integer = 10, fraction = 2, message = "Цена «от»: не больше 10 знаков до запятой и 2 после")
        BigDecimal priceFrom,
        @Parameter(example = "5000000")
        @PositiveOrZero(message = "Цена «до» не может быть отрицательной")
        @Digits(integer = 10, fraction = 2, message = "Цена «до»: не больше 10 знаков до запятой и 2 после")
        BigDecimal priceTo,
        @Parameter(description = "Пробег не больше, км", example = "200000")
        @PositiveOrZero(message = "Пробег не может быть отрицательным")
        Integer mileageTo,
        @Parameter(description = "Город без учёта регистра", example = "Самара")
        @Size(max = 100, message = "Город не длиннее 100 символов")
        String city,
        FuelType fuelType,
        Transmission transmission,
        BodyType bodyType) {

    @Parameter(hidden = true)
    @AssertTrue(message = "Год «от» больше года «до»")
    public boolean isYearRangeValid()
    {
        return yearFrom == null || yearTo == null || yearFrom <= yearTo;
    }

    @Parameter(hidden = true)
    @AssertTrue(message = "Цена «от» больше цены «до»")
    public boolean isPriceRangeValid()
    {
        return priceFrom == null || priceTo == null || priceFrom.compareTo(priceTo) <= 0;
    }

    public boolean hasText()
    {
        return q != null && !q.isBlank();
    }

    public static ListingSearchCriteria empty()
    {
        return new ListingSearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
