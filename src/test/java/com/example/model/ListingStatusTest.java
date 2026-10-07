package com.example.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ListingStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "DRAFT, DRAFT, false",
            "DRAFT, ACTIVE, true",
            "DRAFT, SOLD, false",
            "DRAFT, ARCHIVED, false",
            "ACTIVE, DRAFT, false",
            "ACTIVE, ACTIVE, false",
            "ACTIVE, SOLD, true",
            "ACTIVE, ARCHIVED, true",
            "SOLD, DRAFT, false",
            "SOLD, ACTIVE, false",
            "SOLD, SOLD, false",
            "SOLD, ARCHIVED, false",
            "ARCHIVED, DRAFT, false",
            "ARCHIVED, ACTIVE, true",
            "ARCHIVED, SOLD, false",
            "ARCHIVED, ARCHIVED, false"
    })
    void transitions(ListingStatus from, ListingStatus to, boolean allowed)
    {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
