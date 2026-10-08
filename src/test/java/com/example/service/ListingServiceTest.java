package com.example.service;

import com.example.client.CurrencyClient;
import com.example.dto.AdminListingDto;
import com.example.dto.ListingDto;
import com.example.dto.ListingPriceDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingStats;
import com.example.dto.ListingUpdateRequest;
import com.example.event.ListingPublishedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.exception.ListingStateException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    @Mock
    private ApplicationEventPublisher events;
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private ListingService service;

    // Через new, а не @InjectMocks: тот молча подставит null, если в конструкторе появится новый параметр
    @BeforeEach
    void setUp()
    {
        service = new ListingService(repository, users, reader, new ListingAccessPolicy(), currencyClient, events, registry, Clock.fixed(NOW, ZoneOffset.UTC));
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
    void sellerPublishesDraftAndEventIsSent()
    {
        Listing draft = listing();
        when(repository.findById(1L)).thenReturn(Optional.of(draft));
        when(repository.saveAndFlush(draft)).thenReturn(draft);

        ListingDto result = service.publish(1L, principal(7L, Role.USER));

        assertThat(result.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(result.publishedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new ListingPublishedEvent(1L, 7L, "Toyota", "Supra", new BigDecimal("4500000.00"), NOW));
    }

    @Test
    void republishFromArchiveSendsEventAgain()
    {
        Listing listing = listing();
        listing.publish(NOW.minusSeconds(3600));
        listing.archive(NOW.minusSeconds(60));
        when(repository.findById(1L)).thenReturn(Optional.of(listing));
        when(repository.saveAndFlush(listing)).thenReturn(listing);

        service.publish(1L, principal(7L, Role.USER));

        verify(events).publishEvent(new ListingPublishedEvent(1L, 7L, "Toyota", "Supra", new BigDecimal("4500000.00"), NOW));
    }

    @Test
    void rejectedPublishSendsNoEvent()
    {
        Listing withoutPrice = Listing.draft(user(7L), new ListingDetails("Lada", "Niva", "21214", 83, 2020, 50000, null, "Самара", null), NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(withoutPrice));

        assertThatThrownBy(() -> service.publish(1L, principal(7L, Role.USER)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Для публикации нужна цена");
        verifyNoInteractions(events);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void strangerGets404OnHiddenListing()
    {
        Listing listing = listing();
        listing.publish(NOW);
        listing.archive(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));

        // архив чужому не виден: 404, а не 403
        assertThatThrownBy(() -> service.publish(1L, principal(8L, Role.USER)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Объявление с id: 1 не найдено");
        verifyNoInteractions(events);
    }

    @Test
    void strangerGets403OnActiveListing()
    {
        Listing listing = listing();
        listing.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.markSold(1L, principal(8L, Role.USER)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void strangerCannotArchive()
    {
        Listing listing = listing();
        listing.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.archive(1L, principal(8L, Role.USER)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(repository, never()).saveAndFlush(any());
    }

    // Права проверяются раньше перехода: чужому 403, даже если сам переход тоже запрещён
    @Test
    void rightsAreCheckedBeforeTransition()
    {
        Listing active = listing();
        active.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(active));
        Listing sold = listing();
        sold.publish(NOW);
        sold.markSold(NOW);
        when(repository.findById(2L)).thenReturn(Optional.of(sold));

        assertThatThrownBy(() -> service.publish(1L, principal(8L, Role.USER))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.archive(2L, principal(8L, Role.USER))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(events);
    }

    @Test
    void adminArchivesButCannotPublishOrSell()
    {
        Listing listing = listing();
        listing.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));
        when(repository.saveAndFlush(listing)).thenReturn(listing);
        AppUserPrincipal admin = principal(1L, Role.ADMIN);

        assertThatThrownBy(() -> service.markSold(1L, admin)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.archive(1L, admin).status()).isEqualTo(ListingStatus.ARCHIVED);
        assertThatThrownBy(() -> service.publish(1L, admin)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(events);
    }

    @Test
    void forbiddenTransitionIsConflict()
    {
        when(repository.findById(1L)).thenReturn(Optional.of(listing()));

        assertThatThrownBy(() -> service.markSold(1L, principal(7L, Role.USER)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Нельзя перевести объявление из DRAFT в SOLD");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void publishingActiveAgainIsConflictWithoutEvent()
    {
        Listing listing = listing();
        listing.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.publish(1L, principal(7L, Role.USER)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Нельзя перевести объявление из ACTIVE в ACTIVE");
        verifyNoInteractions(events);
    }

    @Test
    void updateWithCurrentVersionChangesDetails()
    {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "version", Long.valueOf(1000));
        when(repository.findById(1L)).thenReturn(Optional.of(listing));
        when(repository.saveAndFlush(listing)).thenReturn(listing);

        // равные версии, но разные объекты Long: сравнение через == здесь бы сломалось
        ListingDto result = service.update(1L, update(Long.valueOf(1000), new BigDecimal("3900000")), principal(7L, Role.USER));

        assertThat(result.price()).isEqualByComparingTo("3900000");
        assertThat(result.status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(result.sellerId()).isEqualTo(7L);
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void staleVersionIsConflictAndNothingChanges()
    {
        Listing listing = listing();
        ReflectionTestUtils.setField(listing, "version", 3L);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.update(1L, update(2L, new BigDecimal("3900000")), principal(7L, Role.USER)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Объявление уже изменили, актуальная версия 3. Обновите и повторите");
        assertThat(listing.getPrice()).isEqualByComparingTo("4500000");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void strangerCannotEdit()
    {
        Listing active = listing();
        active.publish(NOW);
        ReflectionTestUtils.setField(active, "version", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(active));
        when(repository.findById(2L)).thenReturn(Optional.of(listing()));

        assertThatThrownBy(() -> service.update(1L, update(1L, new BigDecimal("1")), principal(8L, Role.USER)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(2L, update(0L, new BigDecimal("1")), principal(8L, Role.USER)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.update(1L, update(1L, new BigDecimal("1")), principal(1L, Role.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(active.getPrice()).isEqualByComparingTo("4500000");
    }

    @Test
    void sellerDeletesDraft()
    {
        Listing draft = listing();
        when(repository.findById(1L)).thenReturn(Optional.of(draft));

        service.delete(1L, principal(7L, Role.USER));

        verify(repository).delete(draft);
    }

    @Test
    void publishedListingCannotBeDeleted()
    {
        Listing listing = listing();
        listing.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.delete(1L, principal(7L, Role.USER)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Удалить можно только черновик. Опубликованное объявление снимите в архив");
        verify(repository, never()).delete(any(Listing.class));
    }

    @Test
    void strangerCannotDelete()
    {
        Listing listing = listing();
        listing.publish(NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(listing));
        when(repository.findById(2L)).thenReturn(Optional.of(listing()));

        // опубликованное чужое видно: 403; чужой черновик не виден: 404
        assertThatThrownBy(() -> service.delete(1L, principal(8L, Role.USER))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(2L, principal(8L, Role.USER))).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).delete(any(Listing.class));
    }

    @Test
    void adminCannotDeleteSomeoneElsesDraft()
    {
        when(repository.findById(1L)).thenReturn(Optional.of(listing()));

        assertThatThrownBy(() -> service.delete(1L, principal(1L, Role.ADMIN))).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).delete(any(Listing.class));
    }

    @Test
    void changingMissingListingIs404()
    {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(99L, principal(7L, Role.USER)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Объявление с id: 99 не найдено");
    }

    @Test
    void adminListCarriesSellerUsername()
    {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findWithSellerByStatus(ListingStatus.DRAFT, pageable)).thenReturn(new PageImpl<>(List.of(listing()), pageable, 1));

        Page<AdminListingDto> result = service.findAllForAdmin(ListingStatus.DRAFT, pageable);

        assertThat(result.getContent()).extracting(AdminListingDto::sellerUsername).containsExactly("u7");
        assertThat(result.getContent().get(0).listing().id()).isEqualTo(1L);
        verify(repository, never()).findAllWithSeller(any());
    }

    @Test
    void adminListWithoutStatusTakesAll()
    {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAllWithSeller(pageable)).thenReturn(Page.empty());

        service.findAllForAdmin(null, pageable);

        verify(repository).findAllWithSeller(pageable);
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

    private ListingUpdateRequest update(Long version, BigDecimal price)
    {
        return new ListingUpdateRequest(version, "Toyota", "Supra", "2JZ", 330, 1998, 160000, price, "Самара", null);
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
