package com.example.controller;

import com.example.dto.ListingDto;
import com.example.dto.ModerationItemDto;
import com.example.dto.RejectRequest;
import com.example.security.AppUserPrincipal;
import com.example.service.ModerationService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Модерация", description = "Только MODERATOR и ADMIN. Работает, когда включён moderation.required")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/moderation")
public class ModerationController {
    private final ModerationService moderation;
    public ModerationController(ModerationService moderation) { this.moderation=moderation; }
    @Operation(summary = "Очередь", description = "Ждущие проверки, сначала самые старые. Видно, кто какое взял")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Не модератор")
    @GetMapping("/queue")
    public Page<ModerationItemDto> queue(@ParameterObject @PageableDefault(size = 20) Pageable pageable)
    {
        return moderation.queue(ListingSort.fixedOrder(pageable, "Очередь идёт только от старых к новым"));
    }
    @Operation(summary = "Взять в работу", description = "Пока объявление у вас, другой модератор его не возьмёт. Брошенное дольше 30 минут можно забрать")
    @ApiResponse(responseCode = "200", description = "Объявление ваше")
    @ApiResponse(responseCode = "403", description = "Своё объявление проверяет другой модератор")
    @ApiResponse(responseCode = "409", description = "Не ждёт проверки или уже у другого модератора")
    @PostMapping("/listings/{id}/claim")
    public ModerationItemDto claim(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal moderator)
    {
        return moderation.claim(id, moderator);
    }
    @Operation(summary = "Одобрить", description = "Объявление уходит в ленту. Только взятое вами")
    @ApiResponse(responseCode = "200", description = "Опубликовано")
    @ApiResponse(responseCode = "409", description = "Не ждёт проверки или не взято вами")
    @PostMapping("/listings/{id}/approve")
    public ListingDto approve(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal moderator)
    {
        return moderation.approve(id, moderator);
    }
    @Operation(summary = "Отклонить", description = "С причиной, её увидит продавец. Только взятое вами")
    @ApiResponse(responseCode = "200", description = "Отклонено")
    @ApiResponse(responseCode = "400", description = "Причина короче 5 или длиннее 500 символов")
    @ApiResponse(responseCode = "409", description = "Не ждёт проверки или не взято вами")
    @PostMapping("/listings/{id}/reject")
    public ListingDto reject(@PathVariable Long id,
                             @Valid @RequestBody RejectRequest request,
                             @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal moderator)
    {
        return moderation.reject(id, request.reason(), moderator);
    }
}
