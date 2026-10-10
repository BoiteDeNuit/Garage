package com.example.controller;

import com.example.dto.StatusChangeDto;
import com.example.security.AppUserPrincipal;
import com.example.service.ListingHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Объявления", description = "Продажа машин")
@RestController
public class ListingHistoryController {
    private final ListingHistory history;
    public ListingHistoryController(ListingHistory history) { this.history=history; }
    @Operation(summary = "Журнал статусов", description = "Кто, когда и почему менял статус, от создания черновика. Видят продавец и админ")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Объявления нет или журнал вам не виден")
    @GetMapping("/api/listings/{id}/history")
    public List<StatusChangeDto> history(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal viewer)
    {
        return history.of(id, viewer);
    }
}
