package com.example.service;

import com.example.dto.StatusChangeDto;
import com.example.exception.EntityNotFoundException;
import com.example.model.Listing;
import com.example.model.ListingStatus;
import com.example.model.ListingStatusChange;
import com.example.repository.ListingStatusChangeRepository;
import com.example.security.AppUserPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Журнал смены статусов. Запись — в той же транзакции, что и переход: откатился переход — нет и записи
@Component
public class ListingHistory {
    private final ListingStatusChangeRepository changes;
    private final ListingReader reader;
    private final ListingAccessPolicy policy;
    private final Clock clock;
    public ListingHistory(ListingStatusChangeRepository changes, ListingReader reader, ListingAccessPolicy policy, Clock clock)
    {
        this.changes=changes;
        this.reader=reader;
        this.policy=policy;
        this.clock=clock;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Listing listing, @Nullable ListingStatus from, Long actorId, @Nullable String reason)
    {
        changes.save(new ListingStatusChange(listing.getId(), from, listing.getStatus(), actorId, reason, Instant.now(clock)));
    }
    // Журнал видят продавец и админ: там id модераторов и причины отказов. Остальным — 404, как скрытое объявление
    @Transactional(readOnly = true)
    public List<StatusChangeDto> of(Long listingId, @Nullable AppUserPrincipal viewer)
    {
        if(!policy.canSeeHistory(reader.findCard(listingId).sellerId(), viewer))
        {
            throw EntityNotFoundException.listing(listingId);
        }
        return changes.findByListingIdOrderByChangedAtAscIdAsc(listingId).stream()
                .map(change -> new StatusChangeDto(change.getFromStatus(), change.getToStatus(), change.getActorId(), change.getReason(), change.getChangedAt()))
                .toList();
    }
}
