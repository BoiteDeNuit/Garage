package com.example.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ListingSearchCriteriaTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyCriteriaAreValid()
    {
        assertThat(validator.validate(ListingSearchCriteria.empty())).isEmpty();
    }

    @Test
    void openRangesAreValid()
    {
        assertThat(validator.validate(criteria(2000, null, null, new BigDecimal("100")))).isEmpty();
    }

    // compareTo, а не equals: 100 и 100.00 — одна цена
    @Test
    void equalBoundsAreValid()
    {
        assertThat(validator.validate(criteria(2000, 2000, new BigDecimal("100"), new BigDecimal("100.00")))).isEmpty();
    }

    @Test
    void reversedYearsAreRejected()
    {
        assertThat(messages(criteria(2010, 2000, null, null))).containsExactly("Год «от» больше года «до»");
    }

    @Test
    void reversedPricesAreRejected()
    {
        assertThat(messages(criteria(null, null, new BigDecimal("100.01"), new BigDecimal("100.00")))).containsExactly("Цена «от» больше цены «до»");
    }

    @Test
    void negativePriceIsRejected()
    {
        assertThat(messages(criteria(null, null, new BigDecimal("-1"), null))).containsExactly("Цена «от» не может быть отрицательной");
    }

    @ParameterizedTest
    @MethodSource("outOfBounds")
    void boundsAreChecked(ListingSearchCriteria criteria, String message)
    {
        assertThat(messages(criteria)).containsExactly(message);
    }

    static Stream<Arguments> outOfBounds()
    {
        return Stream.of(
                Arguments.of(new ListingSearchCriteria("a".repeat(51), null, null, null, null, null, null, null, null, null, null), "Марка не длиннее 50 символов"),
                Arguments.of(new ListingSearchCriteria(null, "a".repeat(101), null, null, null, null, null, null, null, null, null), "Модель не длиннее 100 символов"),
                Arguments.of(new ListingSearchCriteria(null, null, null, null, null, null, null, "a".repeat(101), null, null, null), "Город не длиннее 100 символов"),
                Arguments.of(new ListingSearchCriteria(null, null, 1800, null, null, null, null, null, null, null, null), "Год «от» не раньше 1885"),
                Arguments.of(new ListingSearchCriteria(null, null, null, 2200, null, null, null, null, null, null, null), "Год «до» не позже 2100"),
                Arguments.of(new ListingSearchCriteria(null, null, null, null, null, new BigDecimal("-1"), null, null, null, null, null), "Цена «до» не может быть отрицательной"),
                Arguments.of(new ListingSearchCriteria(null, null, null, null, null, null, -1, null, null, null, null), "Пробег не может быть отрицательным"),
                Arguments.of(new ListingSearchCriteria(null, null, null, null, new BigDecimal("1E-20000"), null, null, null, null, null, null), "Цена «от»: не больше 10 знаков до запятой и 2 после"),
                Arguments.of(new ListingSearchCriteria(null, null, null, null, null, new BigDecimal("1E+200000"), null, null, null, null, null), "Цена «до»: не больше 10 знаков до запятой и 2 после"));
    }

    private Set<String> messages(ListingSearchCriteria criteria)
    {
        return validator.validate(criteria).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private ListingSearchCriteria criteria(Integer yearFrom, Integer yearTo, BigDecimal priceFrom, BigDecimal priceTo)
    {
        return new ListingSearchCriteria(null, null, yearFrom, yearTo, priceFrom, priceTo, null, null, null, null, null);
    }
}
