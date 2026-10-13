package com.example.service;

import com.example.dto.NotificationDto;
import com.example.dto.UnreadCount;
import com.example.event.ListingPriceDroppedEvent;
import com.example.event.ListingPublishedEvent;
import com.example.model.Notification;
import com.example.model.NotificationType;
import com.example.repository.NotificationRepository;
import com.example.repository.SavedSearchRepository;
import com.example.repository.SearchMatch;
import com.example.security.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationService {
    private final SavedSearchRepository searches;
    private final NotificationRepository notifications;
    private final Clock clock;
    public NotificationService(SavedSearchRepository searches, NotificationRepository notifications, Clock clock)
    {
        this.searches=searches;
        this.notifications=notifications;
        this.clock=clock;
    }
    // Kafka доставляет at-least-once: то же событие может прийти дважды. Второй раз все вставки упрутся
    // в UNIQUE (user_id, event_id), и уведомлений не прибавится. Возвращает, сколько добавилось
    @Transactional
    public int notifyMatchingSearches(ListingPublishedEvent event)
    {
        Instant now = Instant.now(clock);
        int created = 0;
        for(SearchMatch match : searches.findMatching(event.listingId()))
        {
            created += notifications.insertIfAbsent(match.getUserId(), event.eventId(), NotificationType.NEW_LISTING.name(),
                    event.listingId(), match.getSearchId(), now);
        }
        return created;
    }
    // Снижение цены — тем, у кого объявление в избранном. Повтор события так же упирается в UNIQUE
    @Transactional
    public int notifyFavorites(ListingPriceDroppedEvent event)
    {
        return notifications.insertPriceDrops(event.listingId(), event.eventId(), event.oldPrice(), event.newPrice(), Instant.now(clock));
    }
    @Transactional(readOnly = true)
    public Page<NotificationDto> list(AppUserPrincipal user, Pageable pageable)
    {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), pageable).map(this::toDto);
    }
    @Transactional(readOnly = true)
    public UnreadCount unread(AppUserPrincipal user)
    {
        return new UnreadCount(notifications.countByUserIdAndReadAtIsNull(user.getId()));
    }
    @Transactional
    public void markAllRead(AppUserPrincipal user)
    {
        notifications.markAllRead(user.getId(), Instant.now(clock));
    }
    private NotificationDto toDto(Notification notification)
    {
        return new NotificationDto(notification.getId(), notification.getType(), notification.getListingId(),
                notification.getSavedSearchId(), notification.getOldPrice(), notification.getNewPrice(),
                notification.getCreatedAt(), notification.getReadAt() != null);
    }
}
