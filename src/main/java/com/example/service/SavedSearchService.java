package com.example.service;

import com.example.dto.ListingSearchCriteria;
import com.example.dto.SavedSearchDto;
import com.example.dto.SavedSearchRequest;
import com.example.exception.ConflictException;
import com.example.exception.EntityNotFoundException;
import com.example.exception.InvalidRequestException;
import com.example.model.SavedSearch;
import com.example.repository.SavedSearchRepository;
import com.example.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class SavedSearchService {
    static final int MAX_SEARCHES = 20;
    private final SavedSearchRepository searches;
    private final ObjectMapper json;
    private final Clock clock;
    public SavedSearchService(SavedSearchRepository searches, ObjectMapper json, Clock clock)
    {
        this.searches=searches;
        this.json=json;
        this.clock=clock;
    }
    // Поиск без единого фильтра совпал бы с каждым новым объявлением: это не поиск, а подписка на всю ленту.
    // Предел проверяется без блокировки: два одновременных запроса на 20-м поиске дадут 21-й, это не страшно
    @Transactional
    public SavedSearchDto create(SavedSearchRequest request, AppUserPrincipal user)
    {
        if(!request.criteria().hasAnyFilter())
        {
            throw new InvalidRequestException("Нужен хотя бы один фильтр");
        }
        if(searches.countByUserId(user.getId()) >= MAX_SEARCHES)
        {
            throw new ConflictException("Не больше " + MAX_SEARCHES + " сохранённых поисков");
        }
        SavedSearch saved = searches.save(new SavedSearch(user.getId(), request.name().trim(),
                json.writeValueAsString(request.criteria()), Instant.now(clock)));
        return toDto(saved);
    }
    @Transactional(readOnly = true)
    public List<SavedSearchDto> list(AppUserPrincipal user)
    {
        return searches.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toDto).toList();
    }
    // Чужой поиск — 404, как и несуществующий
    @Transactional
    public void delete(Long id, AppUserPrincipal user)
    {
        SavedSearch search = searches.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Сохранённый поиск с id: " + id + " не найден"));
        searches.delete(search);
    }
    private SavedSearchDto toDto(SavedSearch search)
    {
        return new SavedSearchDto(search.getId(), search.getName(), json.readValue(search.getCriteria(), ListingSearchCriteria.class), search.getCreatedAt());
    }
}
