package com.example.service;

import com.example.dto.ListingDto;
import com.example.dto.ListingMapper;
import com.example.exception.EntityNotFoundException;
import com.example.repository.ListingRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Отдельный бин, а не метод ListingService: при вызове через this прокси @Cacheable не сработал бы.
// В кэше карточка без учёта того, кто смотрит: права проверяет ListingService уже после кэша
@Component
public class ListingReader {
    private final ListingRepository repository;
    public ListingReader(ListingRepository repository)
    {
        this.repository=repository;
    }
    @Cacheable(cacheNames = "listings", key = "#id")
    @Transactional(readOnly = true)
    public ListingDto findCard(Long id)
    {
        return repository.findById(id)
                .map(ListingMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Объявление с id: " + id + " не найдено"));
    }
}
