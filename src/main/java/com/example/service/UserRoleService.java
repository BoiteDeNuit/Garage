package com.example.service;

import com.example.dto.UserDto;
import com.example.exception.ConflictException;
import com.example.exception.EntityNotFoundException;
import com.example.exception.InvalidRequestException;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Модераторов назначает админ. Роль берётся из базы на каждый запрос (JwtAuthFilter), поэтому новая роль
// действует сразу, без нового токена. Админа через API не назначить и не снять: он один, из переменных окружения
@Service
public class UserRoleService {
    private final AppUserRepository users;
    public UserRoleService(AppUserRepository users) { this.users=users; }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserDto changeRole(String username, Role role)
    {
        if(role == Role.ADMIN)
        {
            throw new InvalidRequestException("Назначить можно только USER или MODERATOR");
        }
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь " + username + " не найден"));
        if(user.getRole() == Role.ADMIN)
        {
            throw new ConflictException("Роль администратора через API не меняется");
        }
        user.changeRole(role);
        return new UserDto(user.getId(), user.getUsername(), user.getRole());
    }
}
