package com.example.model;

import com.example.exception.ListingStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingTest {
    private static final Instant CREATED = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-08T09:30:00Z");
    private final AppUser seller = new AppUser("seller", "!", Role.USER);

    @Test
    void publishMakesActive()
    {
        Listing listing = draft(new BigDecimal("4500000"), "Самара");

        listing.publish(NOW);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getPublishedAt()).isEqualTo(NOW);
        assertThat(listing.getUpdatedAt()).isEqualTo(NOW);
        assertThat(listing.getCreatedAt()).isEqualTo(CREATED);
    }

    @Test
    void publishWithoutPriceIsRejectedAndNothingChanges()
    {
        Listing listing = draft(null, "Самара");

        assertThatThrownBy(() -> listing.publish(NOW))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Для публикации нужна цена");
        assertUntouched(listing);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void publishWithoutCityIsRejectedAndNothingChanges(String city)
    {
        Listing listing = draft(new BigDecimal("4500000"), city);

        assertThatThrownBy(() -> listing.publish(NOW))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Для публикации нужен город");
        assertUntouched(listing);
    }

    @Test
    void publishTwiceIsConflict()
    {
        Listing listing = published();

        assertThatThrownBy(() -> listing.publish(LATER))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Нельзя перевести объявление из ACTIVE в ACTIVE");
        assertThat(listing.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void archiveKeepsPublishedAtAndUpdatesUpdatedAt()
    {
        Listing listing = published();

        listing.archive(LATER);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ARCHIVED);
        assertThat(listing.getUpdatedAt()).isEqualTo(LATER);
        assertThat(listing.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void republishFromArchiveUpdatesPublishedAt()
    {
        Listing listing = published();
        listing.archive(NOW);

        listing.publish(LATER);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getPublishedAt()).isEqualTo(LATER);
    }

    @Test
    void soldKeepsPublishedAt()
    {
        Listing listing = published();

        listing.markSold(LATER);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
        assertThat(listing.getPublishedAt()).isEqualTo(NOW);
        assertThat(listing.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void soldIsFinal()
    {
        Listing listing = published();
        listing.markSold(NOW);

        assertThatThrownBy(() -> listing.publish(LATER)).isInstanceOf(ListingStateException.class);
        assertThatThrownBy(() -> listing.archive(LATER)).isInstanceOf(ListingStateException.class);
        assertThatThrownBy(() -> listing.markSold(LATER)).isInstanceOf(ListingStateException.class);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void draftCannotBeSoldOrArchived()
    {
        Listing listing = draft(new BigDecimal("4500000"), "Самара");

        assertThatThrownBy(() -> listing.markSold(NOW))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Нельзя перевести объявление из DRAFT в SOLD");
        assertThatThrownBy(() -> listing.archive(NOW))
                .isInstanceOf(ListingStateException.class);
        assertUntouched(listing);
    }

    private Listing draft(BigDecimal price, String city)
    {
        return Listing.draft(seller, new ListingDetails("Toyota", "Supra", "2JZ", 320, 1998, 154000, price, city, null), CREATED);
    }

    private Listing published()
    {
        Listing listing = draft(new BigDecimal("4500000"), "Самара");
        listing.publish(NOW);
        return listing;
    }

    private void assertUntouched(Listing listing)
    {
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(listing.getPublishedAt()).isNull();
        assertThat(listing.getUpdatedAt()).isEqualTo(CREATED);
    }
}
