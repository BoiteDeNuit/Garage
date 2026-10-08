package com.example.controller;

import com.example.dto.AdminListingDto;
import com.example.model.ListingStatus;
import com.example.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Админка", description = "Только ADMIN")
@RestController
@RequestMapping("/api/admin/listings")
public class AdminListingController {
    private final ListingService listings;
    public AdminListingController(ListingService listings) { this.listings=listings; }
    @Operation(summary = "Все объявления", description = "Любые статусы, с логином продавца. По умолчанию сначала новые")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Нельзя сортировать по этому полю или неизвестный статус")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @ApiResponse(responseCode = "403", description = "Не ADMIN")
    @GetMapping
    public Page<AdminListingDto> all(@RequestParam(required = false) ListingStatus status,
                                     @ParameterObject @PageableDefault(size = 20) Pageable pageable)
    {
        return listings.findAllForAdmin(status, ListingSort.forAdminList(pageable));
    }
}
