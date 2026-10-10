package com.example.service;

import com.example.exception.EntityNotFoundException;
import com.example.model.Listing;
import com.example.model.ListingAction;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

// Загрузка объявления под изменение, общая для объявлений и их фото.
// Порядок проверок: нет или не видно -> 404, видно, но не твоё -> 403. Переход и правила статуса проверяет сущность
@Component
public class ListingLoader {
    private final ListingRepository repository;
    private final ListingAccessPolicy policy;
    public ListingLoader(ListingRepository repository, ListingAccessPolicy policy)
    {
        this.repository=repository;
        this.policy=policy;
    }
    public Listing forChange(Long id, AppUserPrincipal actor, ListingAction action)
    {
        Listing listing = repository.findById(id).orElseThrow(() -> EntityNotFoundException.listing(id));
        Long sellerId = listing.getSeller().getId();
        if(!policy.canView(listing.getStatus(), sellerId, actor))
        {
            throw EntityNotFoundException.listing(id);
        }
        if(!policy.canPerform(action, sellerId, actor))
        {
            throw new AccessDeniedException("Недостаточно прав");
        }
        return listing;
    }
}
