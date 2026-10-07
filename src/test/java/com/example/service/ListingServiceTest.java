package com.example.service;

import com.example.client.CurrencyClient;
import com.example.dto.ListingDto;
import com.example.dto.ListingPriceDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingStats;
import com.example.exception.EntityNotFoundException;
import com.example.model.AppUser;
import com.example.model.Listing;
import com.example.model.ListingDetails;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    @Mock
    private ListingRepository repository;
    @Mock
    private AppUserRepository users;
    @Mock
    private ListingReader reader;
    @Mock
    private CurrencyClient currencyClient;
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private ListingService service;

    // Через new, а не @InjectMocks: тот молча подставит null, если в конструкторе появится новый параметр
    @BeforeEach
    void setUp()
    {
        service = new ListingService(repository, users, reader, currencyClient, registry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createSavesDraftOfCurrentUser()
    {
        AppUser seller = user(7L);
        when(users.getReferenceById(7L)).thenReturn(seller);
        when(repository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        ListingDto created = service.create(request(), principal(7L, Role.USER));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(repository).save(captor.capture());
        Listing saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(saved.getSeller()).isSameAs(seller);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(created.sellerId()).isEqualTo(7L);
        assertThat(created.brand()).isEqualTo("Toyota");
    }

    @Test
    void createIncrementsCounter()
    {
        when(users.getReferenceById(7L)).thenReturn(user(7L));
        when(repository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(request(), principal(7L, Role.USER));

        assertThat(registry.get("listings.added").counter().count()).isEqualTo(1.0);
    }

    @Test
    void anonymousSeesActiveCard()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.ACTIVE, 7L));

        assertThat(service.get(1L, null).status()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void anonymousSeesSoldCard()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.SOLD, 7L));

        assertThat(service.get(1L, null).status()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void hiddenDraftLooksLikeMissing()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.DRAFT, 7L));

        assertThatThrownBy(() -> service.get(1L, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Объявление с id: 1 не найдено");
        assertThatThrownBy(() -> service.get(1L, principal(8L, Role.USER)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Объявление с id: 1 не найдено");
    }

    @Test
    void sellerAndAdminSeeDraftAndArchive()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.DRAFT, 7L));
        when(reader.findCard(2L)).thenReturn(card(ListingStatus.ARCHIVED, 7L));

        assertThat(service.get(1L, principal(7L, Role.USER)).status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(service.get(1L, principal(1L, Role.ADMIN)).status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(service.get(2L, principal(7L, Role.USER)).status()).isEqualTo(ListingStatus.ARCHIVED);
    }

    // id больше 127: Long из разных мест — разные объекты, сравнение через == здесь бы сломалось
    @Test
    void sellerIsComparedByValue()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.DRAFT, Long.valueOf(1000)));

        assertThat(service.get(1L, principal(Long.valueOf(1000), Role.USER)).status()).isEqualTo(ListingStatus.DRAFT);
    }

    @Test
    void findPublicReturnsOnlyActive()
    {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByStatus(ListingStatus.ACTIVE, pageable)).thenReturn(new PageImpl<>(List.of(listing()), pageable, 1));

        Page<ListingDto> result = service.findPublic(null, pageable);

        assertThat(result.getContent()).extracting(ListingDto::brand).containsExactly("Toyota");
        verify(repository, never()).findByStatusAndBrandIgnoreCase(any(), any(), any());
    }

    @Test
    void findPublicFiltersByBrand()
    {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByStatusAndBrandIgnoreCase(ListingStatus.ACTIVE, "toyota", pageable)).thenReturn(Page.empty());

        service.findPublic("toyota", pageable);

        verify(repository).findByStatusAndBrandIgnoreCase(ListingStatus.ACTIVE, "toyota", pageable);
    }

    @Test
    void statsOfEmptyCatalog()
    {
        when(repository.countByStatus(ListingStatus.ACTIVE)).thenReturn(0L);
        when(repository.averageHorsePower(ListingStatus.ACTIVE)).thenReturn(0.0);
        when(repository.findFirstByStatusOrderByHorsePowerDesc(ListingStatus.ACTIVE)).thenReturn(Optional.empty());

        ListingStats stats = service.stats();

        assertThat(stats.activeCount()).isZero();
        assertThat(stats.strongestModel()).isEqualTo("Каталог пуст");
    }

    @Test
    void statsCountOnlyActive()
    {
        when(repository.countByStatus(ListingStatus.ACTIVE)).thenReturn(2L);
        when(repository.averageHorsePower(ListingStatus.ACTIVE)).thenReturn(415.0);
        when(repository.findFirstByStatusOrderByHorsePowerDesc(ListingStatus.ACTIVE)).thenReturn(Optional.of(listing()));

        ListingStats stats = service.stats();

        assertThat(stats.activeCount()).isEqualTo(2);
        assertThat(stats.averageHp()).isEqualTo(415.0);
        assertThat(stats.strongestModel()).isEqualTo("Supra");
    }

    @Test
    void priceIsConvertedByRate()
    {
        when(repository.findByIdAndStatusIn(eq(1L), anyCollection())).thenReturn(Optional.of(listing()));
        when(currencyClient.rateToRub("usd")).thenReturn(new BigDecimal("90"));

        ListingPriceDto price = service.priceIn(1L, "usd");

        assertThat(price.currency()).isEqualTo("USD");
        assertThat(price.price()).isEqualByComparingTo("50000.00");
    }

    @Test
    void priceLooksOnlyAtActiveAndSold()
    {
        when(repository.findByIdAndStatusIn(eq(1L), anyCollection())).thenReturn(Optional.of(listing()));
        when(currencyClient.rateToRub("USD")).thenReturn(new BigDecimal("90"));

        service.priceIn(1L, "USD");

        verify(repository).findByIdAndStatusIn(1L, EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD));
    }

    @Test
    void priceOfHiddenListingIs404WithoutCallingCbr()
    {
        when(repository.findByIdAndStatusIn(eq(1L), anyCollection())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.priceIn(1L, "USD"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Объявление с id: 1 не найдено");
        verifyNoInteractions(currencyClient);
    }

    @Test
    void priceWithoutPriceIs404()
    {
        Listing listing = Listing.draft(user(7L), new ListingDetails("Lada", "Niva", "21214", 83, 2020, 50000, null, null, null), NOW);
        when(repository.findByIdAndStatusIn(eq(1L), anyCollection())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.priceIn(1L, "USD"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("не указана цена");
        verifyNoInteractions(currencyClient);
    }

    private ListingRequest request()
    {
        return new ListingRequest("Toyota", "Supra", "2JZ", 320, 1998, 154000, new BigDecimal("4500000"), "Самара", null);
    }

    private Listing listing()
    {
        Listing listing = Listing.draft(user(7L), new ListingDetails("Toyota", "Supra", "2JZ", 320, 1998, 154000, new BigDecimal("4500000"), "Самара", null), NOW);
        ReflectionTestUtils.setField(listing, "id", 1L);
        return listing;
    }

    private ListingDto card(ListingStatus status, Long sellerId)
    {
        return new ListingDto(1L, sellerId, status, "Toyota", "Supra", "2JZ", 320, 1998, 154000,
                new BigDecimal("4500000"), "Самара", null, NOW, NOW, null, 0L);
    }

    private AppUser user(Long id)
    {
        AppUser user = new AppUser("u" + id, "!", Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private AppUserPrincipal principal(Long id, Role role)
    {
        return new AppUserPrincipal(id, "u" + id, "!", role);
    }
}
