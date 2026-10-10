package com.example.controller;

import com.example.dto.NotificationDto;
import com.example.dto.UnreadCount;
import com.example.security.AppUserPrincipal;
import com.example.service.NotificationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Уведомления", description = "Новые объявления под сохранённые поиски. Только вошедший пользователь")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications=notifications; }
    @Operation(summary = "Мои уведомления", description = "Сначала новые. Сортировка не меняется")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Задана сортировка или слишком большой номер страницы")
    @GetMapping
    public Page<NotificationDto> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable,
                                      @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        return notifications.list(user, ListingSort.fixedOrder(pageable, "Уведомления идут только от новых к старым"));
    }
    @Operation(summary = "Сколько непрочитанных")
    @GetMapping("/unread-count")
    public UnreadCount unread(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        return notifications.unread(user);
    }
    @Operation(summary = "Прочитать все")
    @ApiResponse(responseCode = "204", description = "Непрочитанных не осталось")
    @PostMapping("/read")
    public ResponseEntity<Void> markAllRead(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal user)
    {
        notifications.markAllRead(user);
        return ResponseEntity.noContent().build();
    }
}
