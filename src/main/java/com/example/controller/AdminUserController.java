package com.example.controller;

import com.example.dto.BanRequest;
import com.example.dto.RoleChangeRequest;
import com.example.dto.UserBanDto;
import com.example.dto.UserDto;
import com.example.security.AppUserPrincipal;
import com.example.service.UserBanService;
import com.example.service.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Админка", description = "Только ADMIN")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserRoleService roles;
    private final UserBanService bans;
    public AdminUserController(UserRoleService roles, UserBanService bans)
    {
        this.roles=roles;
        this.bans=bans;
    }
    @Operation(summary = "Назначить роль", description = "USER или MODERATOR. Действует сразу, без нового входа")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Роль ADMIN или не из списка")
    @ApiResponse(responseCode = "404", description = "Нет такого пользователя")
    @ApiResponse(responseCode = "409", description = "Это администратор")
    @PutMapping("/{username}/role")
    public UserDto changeRole(@PathVariable String username, @Valid @RequestBody RoleChangeRequest request)
    {
        return roles.changeRole(username, request.role());
    }
    @Operation(summary = "Заблокировать", description = "Действует сразу: запросы с токеном и вход получают 403. "
            + "Опубликованные объявления уходят в архив, ждущие проверки отклоняются с этой причиной")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Причина короче 5 или длиннее 500 символов")
    @ApiResponse(responseCode = "404", description = "Нет такого пользователя")
    @ApiResponse(responseCode = "409", description = "Уже заблокирован или это администратор")
    @PostMapping("/{username}/ban")
    public UserBanDto ban(@PathVariable String username, @Valid @RequestBody BanRequest request,
                          @AuthenticationPrincipal AppUserPrincipal admin)
    {
        return bans.ban(username, request.reason(), admin);
    }
    @Operation(summary = "Разблокировать", description = "Объявления остаются в архиве и отклонёнными, публикует их сам продавец")
    @ApiResponse(responseCode = "204", description = "Разблокирован или не был заблокирован")
    @ApiResponse(responseCode = "404", description = "Нет такого пользователя")
    @DeleteMapping("/{username}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@PathVariable String username)
    {
        bans.unban(username);
    }
}
