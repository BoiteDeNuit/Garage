package com.example.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ListingStatusTest {
    // Все разрешённые переходы. Остальные из 36 пар запрещены: новый статус без строки здесь уронит тест
    private static final Set<String> ALLOWED = Set.of(
            "DRAFT->ACTIVE", "DRAFT->PENDING_REVIEW",
            "ARCHIVED->ACTIVE", "ARCHIVED->PENDING_REVIEW",
            "REJECTED->ACTIVE", "REJECTED->PENDING_REVIEW",
            "PENDING_REVIEW->ACTIVE", "PENDING_REVIEW->REJECTED",
            "ACTIVE->SOLD", "ACTIVE->ARCHIVED");

    static Stream<Arguments> everyPair()
    {
        return Arrays.stream(ListingStatus.values())
                .flatMap(from -> Arrays.stream(ListingStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void transitions(ListingStatus from, ListingStatus to)
    {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.contains(from + "->" + to));
    }
}
