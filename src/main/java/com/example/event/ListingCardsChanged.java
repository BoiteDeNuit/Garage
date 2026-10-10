package com.example.event;

import java.util.List;

// Объявления изменены массовым UPDATE, мимо @CacheEvict. Их карточки уберёт из кэша ListingCardsEvictor после коммита
public record ListingCardsChanged(List<Long> listingIds) {
}
