package com.example.controller;

import com.example.dto.RoleChangeRequest;
import com.example.dto.UserDto;
import com.example.service.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Админка", description = "Только ADMIN")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserRoleService roles;
    public AdminUserController(UserRoleService roles) { this.roles=roles; }
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
}
