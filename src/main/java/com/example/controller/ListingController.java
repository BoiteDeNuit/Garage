package com.example.controller;

import com.example.dto.ListingDto;
import com.example.dto.ListingPriceDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingSearchCriteria;
import com.example.dto.ListingStats;
import com.example.dto.ListingUpdateRequest;
import com.example.security.AppUserPrincipal;
import com.example.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Объявления", description = "Продажа машин")
@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingService listings;
    public ListingController(ListingService listings) { this.listings=listings; }
    @Operation(summary = "Лента объявлений", description = "Только опубликованные. Фильтры необязательные, границы «от» и «до» включаются. "
            + "Пагинация, сортировка: publishedAt, createdAt, price, year, mileageKm, horsePower, id")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Неверный фильтр: «от» больше «до», значение не из списка, нельзя сортировать по этому полю")
    @GetMapping
    public Page<ListingDto> feed(@Valid @ParameterObject ListingSearchCriteria criteria,
                                 @ParameterObject @PageableDefault(size = 20) Pageable pageable)
    {
        return listings.findPublic(criteria, ListingSort.forPublicFeed(pageable));
    }
    @Operation(summary = "Объявление по id", description = "Черновик и архив видят только продавец и админ")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @GetMapping("/{id}")
    public ListingDto one(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal viewer)
    {
        return listings.get(id, viewer);
    }
    @Operation(summary = "Создать черновик", description = "Продавец — тот, чей токен")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Создан")
    @ApiResponse(responseCode = "400", description = "Ошибка в данных")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @PostMapping
    public ResponseEntity<ListingDto> create(@Valid @RequestBody ListingRequest request,
                                             @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal seller)
    {
        ListingDto created = listings.create(request, seller);
        return ResponseEntity.created(URI.create("/api/listings/" + created.id())).body(created);
    }
    @Operation(summary = "Изменить объявление", description = "Все поля и version из последнего GET. Статус и продавец не меняются")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "OK, в ответе новая version")
    @ApiResponse(responseCode = "400", description = "Ошибка в данных или нет version")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @ApiResponse(responseCode = "403", description = "Не продавец")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @ApiResponse(responseCode = "409", description = "Устаревшая version, объявление продано или у опубликованного убирают цену/город")
    @PutMapping("/{id}")
    public ListingDto update(@PathVariable Long id,
                             @Valid @RequestBody ListingUpdateRequest request,
                             @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        return listings.update(id, request, actor);
    }
    @Operation(summary = "Опубликовать", description = "Из черновика или архива. Нужны цена и город")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Не продавец")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @ApiResponse(responseCode = "409", description = "Переход запрещён, не хватает цены/города или объявление изменили одновременно")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @PostMapping("/{id}/publish")
    public ListingDto publish(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        return listings.publish(id, actor);
    }
    @Operation(summary = "Отметить проданным", description = "Только из опубликованного. Проданное больше не меняется")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Не продавец")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @ApiResponse(responseCode = "409", description = "Переход запрещён или объявление изменили одновременно")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @PostMapping("/{id}/sold")
    public ListingDto sold(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        return listings.markSold(id, actor);
    }
    @Operation(summary = "Снять в архив", description = "Продавец или ADMIN. Только из опубликованного")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Не продавец и не ADMIN")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @ApiResponse(responseCode = "409", description = "Переход запрещён или объявление изменили одновременно")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @PostMapping("/{id}/archive")
    public ListingDto archive(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        return listings.archive(id, actor);
    }
    @Operation(summary = "Удалить черновик", description = "Только продавец и только черновик. Опубликованное снимают в архив")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Удалено")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @ApiResponse(responseCode = "403", description = "Не продавец")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @ApiResponse(responseCode = "409", description = "Не черновик")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        listings.delete(id, actor);
    }
    @Operation(summary = "Статистика по опубликованным")
    @GetMapping("/stats")
    public ListingStats stats()
    {
        return listings.stats();
    }
    @Operation(summary = "Цена в валюте", description = "Курс ЦБ на сегодня")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Неизвестная валюта")
    @ApiResponse(responseCode = "404", description = "Нет объявления или цены")
    @ApiResponse(responseCode = "503", description = "ЦБ недоступен")
    @GetMapping("/{id}/price")
    public ListingPriceDto price(@PathVariable Long id, @RequestParam(defaultValue = "USD") String currency)
    {
        return listings.priceIn(id,currency);
    }
}
