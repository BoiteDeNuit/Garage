package com.example.model;

import com.example.exception.ListingStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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

    @Test
    void draftDetailsAreUpdated()
    {
        Listing listing = draft(null, null);

        listing.updateDetails(details(new BigDecimal("3900000"), "Тольятти"), LATER);

        assertThat(listing.getPrice()).isEqualByComparingTo("3900000");
        assertThat(listing.getCity()).isEqualTo("Тольятти");
        assertThat(listing.getMileageKm()).isEqualTo(160000);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(listing.getUpdatedAt()).isEqualTo(LATER);
        assertThat(listing.getSeller()).isSameAs(seller);
    }

    @Test
    void activeKeepsStatusAfterEdit()
    {
        Listing listing = published();

        listing.updateDetails(details(new BigDecimal("4100000"), "Самара"), LATER);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.getPrice()).isEqualByComparingTo("4100000");
        assertThat(listing.getPublishedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, Самара", "4100000, NULL", "4100000, '  '"}, nullValues = "NULL")
    void activeCannotLosePriceOrCity(BigDecimal price, String city)
    {
        Listing listing = published();

        assertThatThrownBy(() -> listing.updateDetails(details(price, city), LATER))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("У опубликованного объявления должны быть цена и город");
        assertThat(listing.getPrice()).isEqualByComparingTo("4500000");
        assertThat(listing.getCity()).isEqualTo("Самара");
        assertThat(listing.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void soldCannotBeEdited()
    {
        Listing listing = published();
        listing.markSold(NOW);

        assertThatThrownBy(() -> listing.updateDetails(details(new BigDecimal("1"), "Самара"), LATER))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Проданное объявление менять нельзя");
        assertThat(listing.getPrice()).isEqualByComparingTo("4500000");
    }

    @Test
    void archivedCanBeEditedWithoutPrice()
    {
        Listing listing = published();
        listing.archive(NOW);

        listing.updateDetails(details(null, null), LATER);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ARCHIVED);
        assertThat(listing.getPrice()).isNull();
    }

    @Test
    void onlyDraftIsDeletable()
    {
        draft(new BigDecimal("4500000"), "Самара").checkDeletable();

        Listing listing = published();
        assertThatThrownBy(listing::checkDeletable)
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Удалить можно только черновик. Опубликованное объявление снимите в архив");
        listing.archive(LATER);
        assertThatThrownBy(listing::checkDeletable).isInstanceOf(ListingStateException.class);
    }

    // PUT заменяет всё описание: характеристика, которую не прислали, стирается
    @Test
    void specsAreSetAndClearedByEdit()
    {
        Listing listing = draft(null, null);
        ListingDetails withSpecs = new ListingDetails("Toyota", "Supra", "2JZ", 330, 1998, 160000,
                FuelType.PETROL, Transmission.MANUAL, BodyType.COUPE, null, null, null);

        listing.updateDetails(withSpecs, LATER);

        assertThat(listing.getFuelType()).isEqualTo(FuelType.PETROL);
        assertThat(listing.getTransmission()).isEqualTo(Transmission.MANUAL);
        assertThat(listing.getBodyType()).isEqualTo(BodyType.COUPE);

        listing.updateDetails(details(null, null), LATER);

        assertThat(listing.getFuelType()).isNull();
        assertThat(listing.getTransmission()).isNull();
        assertThat(listing.getBodyType()).isNull();
    }

    @Test
    void newDraftsWithSameDetailsAreDifferent()
    {
        Listing first = draft(new BigDecimal("4500000"), "Самара");
        Listing second = draft(new BigDecimal("4500000"), "Самара");

        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo(second);
    }

    // id больше 127: Long из кэша valueOf тут не выручит, сравнение через == дало бы false
    @Test
    void sameIdMeansSameListing()
    {
        Listing first = withId(draft(new BigDecimal("4500000"), "Самара"), 1000L);
        Listing second = withId(draft(null, "Тольятти"), 1000L);
        Listing third = withId(draft(new BigDecimal("4500000"), "Самара"), 1001L);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(third);
    }

    @Test
    void hashCodeDoesNotChangeWhenIdAppears()
    {
        Listing listing = draft(new BigDecimal("4500000"), "Самара");
        int before = listing.hashCode();
        Set<Listing> set = new HashSet<>();
        set.add(listing);

        withId(listing, 1000L);

        assertThat(listing.hashCode()).isEqualTo(before);
        // set.contains, а не assertThat(set).contains: AssertJ перебирает элементы через equals и хэш не проверяет
        assertThat(set.contains(listing)).isTrue();
    }

    @Test
    void notEqualToNullOrOtherType()
    {
        Listing listing = withId(draft(new BigDecimal("4500000"), "Самара"), 1000L);

        assertThat(listing.equals(null)).isFalse();
        assertThat(listing.equals(1000L)).isFalse();
    }

    private Listing withId(Listing listing, Long id)
    {
        ReflectionTestUtils.setField(listing, "id", id);
        return listing;
    }

    private Listing draft(BigDecimal price, String city)
    {
        return Listing.draft(seller, new ListingDetails("Toyota", "Supra", "2JZ", 320, 1998, 154000, null, null, null, price, city, null), CREATED);
    }

    private ListingDetails details(BigDecimal price, String city)
    {
        return new ListingDetails("Toyota", "Supra", "2JZ", 330, 1998, 160000, null, null, null, price, city, "Свежее ТО");
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
