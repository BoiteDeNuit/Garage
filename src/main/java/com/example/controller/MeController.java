package com.example.controller;

import com.example.dto.ListingDto;
import com.example.dto.UserDto;
import com.example.model.ListingStatus;
import com.example.security.AppUserPrincipal;
import com.example.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Префикс /api/me, а не /api/listings/my: тот попал бы под правило GET /api/listings/** для всех
@Tag(name = "Мой кабинет", description = "Только вошедший пользователь")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me")
public class MeController {
    private final ListingService listings;
    public MeController(ListingService listings) { this.listings=listings; }
    // Данные берутся из принципала, который JwtAuthFilter уже загрузил: второго запроса в базу нет
    @Operation(summary = "Кто я")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @GetMapping
    public UserDto me(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal principal)
    {
        return new UserDto(principal.getId(), principal.getUsername(), principal.getRole());
    }
    @Operation(summary = "Мои объявления", description = "Все статусы, фильтр ?status=. По умолчанию сначала новые")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Нельзя сортировать по этому полю или неизвестный статус")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @GetMapping("/listings")
    public Page<ListingDto> myListings(@RequestParam(required = false) ListingStatus status,
                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                       @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal principal)
    {
        return listings.findMine(principal, status, ListingSort.forOwnerList(pageable));
    }
}
