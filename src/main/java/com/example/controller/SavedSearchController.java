package com.example.controller;

import com.example.dto.SavedSearchDto;
import com.example.dto.SavedSearchRequest;
import com.example.security.AppUserPrincipal;
import com.example.service.SavedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Сохранённые поиски", description = "Только вошедший пользователь. Фильтры те же, что у ленты")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me/searches")
public class SavedSearchController {
    private final SavedSearchService searches;
    public SavedSearchController(SavedSearchService searches) { this.searches=searches; }
    @Operation(summary = "Сохранить поиск", description = "Нужен хотя бы один фильтр. Не больше 20 поисков")
    @ApiResponse(responseCode = "201", description = "Сохранён")
    @ApiResponse(responseCode = "400", description = "Нет названия, ни одного фильтра или фильтр неверный")
    @ApiResponse(responseCode = "409", description = "Уже 20 поисков")
    @PostMapping
    public ResponseEntity<SavedSearchDto> create(@Valid @RequestBody SavedSearchRequest request,
                                                 @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(searches.create(request, user));
    }
    @Operation(summary = "Мои поиски", description = "Сначала новые")
    @GetMapping
    public List<SavedSearchDto> list(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        return searches.list(user);
    }
    @Operation(summary = "Удалить поиск")
    @ApiResponse(responseCode = "204", description = "Удалён")
    @ApiResponse(responseCode = "404", description = "Нет такого поиска у вас")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        searches.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
