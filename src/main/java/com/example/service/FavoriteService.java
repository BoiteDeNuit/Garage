package com.example.service;

import com.example.dto.ListingDto;
import com.example.dto.ListingMapper;
import com.example.exception.EntityNotFoundException;
import com.example.repository.FavoriteRepository;
import com.example.security.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// PUT и DELETE идемпотентны: повтор не ошибка, клиент может слать их сколько угодно раз
@Service
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final ListingReader reader;
    private final ListingAccessPolicy policy;
    private final Clock clock;
    public FavoriteService(FavoriteRepository favorites, ListingReader reader, ListingAccessPolicy policy, Clock clock)
    {
        this.favorites=favorites;
        this.reader=reader;
        this.policy=policy;
        this.clock=clock;
    }
    // Добавить можно только то, что видишь: чужой черновик — 404, как и при просмотре
    @Transactional
    public void add(Long listingId, AppUserPrincipal user)
    {
        ListingDto card = reader.findCard(listingId);
        if(!policy.canView(card.status(), card.sellerId(), user))
        {
            throw EntityNotFoundException.listing(listingId);
        }
        favorites.add(user.getId(), listingId, Instant.now(clock));
    }
    // Без проверки объявления: убрать то, чего нет, — тоже успех, и по ответу не узнать, есть ли объявление
    @Transactional
    public void remove(Long listingId, AppUserPrincipal user)
    {
        favorites.remove(user.getId(), listingId);
    }
    // Только опубликованные и проданные: снятое в архив из избранного пропадает, вернётся после повторной публикации
    @Transactional(readOnly = true)
    public Page<ListingDto> list(AppUserPrincipal user, Pageable pageable)
    {
        return favorites.findListings(user.getId(), policy.publicStatuses(), pageable).map(ListingMapper::toDto);
    }
}
