package com.example.controller;

import com.example.dto.ListingDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingUpdateRequest;
import com.example.exception.EntityNotFoundException;
import com.example.exception.ListingStateException;
import com.example.model.Listing;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.security.AppUserPrincipal;
import com.example.security.JwtService;
import com.example.security.SecurityConfig;
import com.example.security.SecurityErrorWriter;
import com.example.service.ListingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Срез с настоящей безопасностью: SecurityConfig и JwtAuthFilter работают, замоканы только их зависимости
@WebMvcTest(ListingController.class)
@Import({SecurityConfig.class, SecurityErrorWriter.class})
class ListingControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    ListingService service;
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    UserDetailsService userDetailsService;
    private final AppUserPrincipal seller = new AppUserPrincipal(7L, "seller", "!", Role.USER);

    @Test
    void anonymousGetsCardWithoutPrincipal() throws Exception
    {
        when(service.get(eq(1L), isNull())).thenReturn(card(1L));

        mockMvc.perform(get("/api/listings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void loggedInUserIsPassedToService() throws Exception
    {
        when(service.get(eq(1L), any())).thenReturn(card(1L));

        mockMvc.perform(get("/api/listings/1").with(user(seller)))
                .andExpect(status().isOk());

        verify(service).get(eq(1L), argThat(viewer -> viewer != null && viewer.getId().equals(7L)));
    }

    @Test
    void returns404() throws Exception
    {
        when(service.get(eq(99L), any())).thenThrow(new EntityNotFoundException("Объявление с id: 99 не найдено"));

        mockMvc.perform(get("/api/listings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Объявление с id: 99 не найдено"));
    }

    @Test
    void returns400WhenIdIsNotNumber() throws Exception
    {
        mockMvc.perform(get("/api/listings/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("id, прислали: abc")));
    }

    @Test
    void createWithoutTokenIs401() throws Exception
    {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Требуется аутентификация"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void createReturns201WithLocation() throws Exception
    {
        when(service.create(any(), any())).thenReturn(card(5L));

        mockMvc.perform(post("/api/listings").with(user(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/listings/5"))
                .andExpect(jsonPath("$.id").value(5));

        verify(service).create(any(ListingRequest.class), argThat(principal -> principal.getId().equals(7L)));
    }

    @Test
    void invalidListingIs400() throws Exception
    {
        ListingRequest invalid = new ListingRequest("", "Supra", "2JZ", 0, 1998, 150000, new BigDecimal("12345678901"), "Самара", null);

        mockMvc.perform(post("/api/listings").with(user(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Марка не должна быть пустой")))
                .andExpect(jsonPath("$.message").value(containsString("Цена")));

        verify(service, never()).create(any(), any());
    }

    @Test
    void returns415ForNonJsonBody() throws Exception
    {
        mockMvc.perform(post("/api/listings").with(user(seller))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void returns405ForUnsupportedMethod() throws Exception
    {
        mockMvc.perform(patch("/api/listings/1").with(user(seller)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Метод PATCH не поддерживается для данного адреса"));
    }

    @Test
    void returns404ForUnknownPath() throws Exception
    {
        mockMvc.perform(get("/api/nothing").with(user(seller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("/api/nothing")));
    }

    @Test
    void feedIsSortedByPublishedAtThenId() throws Exception
    {
        when(service.findPublic(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(card(1L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].brand").value("Toyota"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findPublic(isNull(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
    }

    @Test
    void limitsPageSizeToMaximum() throws Exception
    {
        when(service.findPublic(any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/listings").param("size", "1000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findPublic(any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void sortOutsideWhitelistIs400() throws Exception
    {
        mockMvc.perform(get("/api/listings").param("sort", "seller.passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Нельзя сортировать по полю: seller.passwordHash"));

        verify(service, never()).findPublic(any(), any());
    }

    @Test
    void clientSortGetsIdAsTieBreaker() throws Exception
    {
        when(service.findPublic(any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/listings").param("sort", "price,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findPublic(any(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id")));
    }

    @Test
    void publishReturnsUpdatedListing() throws Exception
    {
        when(service.publish(eq(1L), any())).thenReturn(card(1L));

        mockMvc.perform(post("/api/listings/1/publish").with(user(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(service).publish(eq(1L), argThat(actor -> actor.getId().equals(7L)));
    }

    @Test
    void publishWithoutTokenIs401() throws Exception
    {
        mockMvc.perform(post("/api/listings/1/publish"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).publish(any(), any());
    }

    @Test
    void serviceDenialIs403Not500() throws Exception
    {
        when(service.markSold(eq(1L), any())).thenThrow(new AccessDeniedException("Недостаточно прав"));

        mockMvc.perform(post("/api/listings/1/sold").with(user(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Недостаточно прав"));
    }

    @Test
    void stateConflictIs409() throws Exception
    {
        when(service.archive(eq(1L), any())).thenThrow(ListingStateException.transition(ListingStatus.DRAFT, ListingStatus.ARCHIVED));

        mockMvc.perform(post("/api/listings/1/archive").with(user(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя перевести объявление из DRAFT в ARCHIVED"));
    }

    @Test
    void concurrentChangeIs409Not500() throws Exception
    {
        when(service.publish(eq(1L), any())).thenThrow(new ObjectOptimisticLockingFailureException(Listing.class, 1L));

        mockMvc.perform(post("/api/listings/1/publish").with(user(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Объявление изменили одновременно с вами, обновите и повторите"));
    }

    @Test
    void updateReturnsListing() throws Exception
    {
        when(service.update(eq(1L), any(), any())).thenReturn(card(1L));

        mockMvc.perform(put("/api/listings/1").with(user(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit(0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        verify(service).update(eq(1L), argThat(request -> request.version().equals(0L)), argThat(actor -> actor.getId().equals(7L)));
    }

    @Test
    void updateWithoutVersionIs400() throws Exception
    {
        mockMvc.perform(put("/api/listings/1").with(user(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Укажите версию объявления")));

        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void updateWithoutTokenIs401() throws Exception
    {
        mockMvc.perform(put("/api/listings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit(0L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReturns204() throws Exception
    {
        mockMvc.perform(delete("/api/listings/1").with(user(seller)))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(1L), argThat(actor -> actor.getId().equals(7L)));
    }

    @Test
    void deleteWithoutTokenIs401() throws Exception
    {
        mockMvc.perform(delete("/api/listings/1"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).delete(any(), any());
    }

    private ListingUpdateRequest edit(Long version)
    {
        return new ListingUpdateRequest(version, "Toyota", "Supra", "2JZ", 320, 1998, 154000, new BigDecimal("4400000"), "Самара", null);
    }

    private ListingRequest supra()
    {
        return new ListingRequest("Toyota", "Supra", "2JZ", 320, 1998, 154000, new BigDecimal("4500000"), "Самара", null);
    }

    private ListingDto card(Long id)
    {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        return new ListingDto(id, 7L, ListingStatus.ACTIVE, "Toyota", "Supra", "2JZ", 320, 1998, 154000,
                new BigDecimal("4500000"), "Самара", null, now, now, now, 1L);
    }
}
