package com.example.service;

import com.example.dto.ListingDto;
import com.example.exception.EntityNotFoundException;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.repository.FavoriteRepository;
import com.example.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    @Mock
    private FavoriteRepository favorites;
    @Mock
    private ListingReader reader;
    private FavoriteService service;

    @BeforeEach
    void setUp()
    {
        service = new FavoriteService(favorites, reader, new ListingAccessPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void visibleListingIsAddedWithCurrentTime()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.ACTIVE));

        service.add(1L, user(8L));

        verify(favorites).add(8L, 1L, NOW);
    }

    // Чужой черновик: та же проверка, что при просмотре, в базу ничего не пишется
    @Test
    void hiddenListingIsNotFound()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.DRAFT));

        assertThatThrownBy(() -> service.add(1L, user(8L))).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(favorites);
    }

    // Удаление не смотрит на объявление: по ответу не узнать, есть ли оно
    @Test
    void removeDoesNotReadListing()
    {
        service.remove(1L, user(8L));

        verify(favorites).remove(8L, 1L);
        verifyNoInteractions(reader);
    }

    private ListingDto card(ListingStatus status)
    {
        return new ListingDto(1L, 7L, status, "Toyota", "Supra", "2JZ", 320, 1998, 154000, null, null, null,
                new BigDecimal("4500000"), "Самара", null, NOW, NOW, null, 0L);
    }

    private AppUserPrincipal user(Long id)
    {
        return new AppUserPrincipal(id, "u" + id, "!", Role.USER);
    }
}
