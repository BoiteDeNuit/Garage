package com.example.controller;

import com.example.dto.ListingDto;
import com.example.security.AppUserPrincipal;
import com.example.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Избранное", description = "Только вошедший пользователь")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites) { this.favorites=favorites; }
    @Operation(summary = "Добавить в избранное", description = "Повтор ничего не меняет")
    @ApiResponse(responseCode = "204", description = "В избранном")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @PutMapping("/listings/{id}/favorite")
    public ResponseEntity<Void> add(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        favorites.add(id, user);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Убрать из избранного", description = "Повтор и чужой id тоже 204")
    @ApiResponse(responseCode = "204", description = "Не в избранном")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @DeleteMapping("/listings/{id}/favorite")
    public ResponseEntity<Void> remove(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        favorites.remove(id, user);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Моё избранное", description = "Опубликованные и проданные, недавно добавленные первыми. Сортировка не меняется")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Задана сортировка или слишком большой номер страницы")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @GetMapping("/me/favorites")
    public Page<ListingDto> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                 @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        return favorites.list(user, ListingSort.forFavorites(pageable));
    }
}
