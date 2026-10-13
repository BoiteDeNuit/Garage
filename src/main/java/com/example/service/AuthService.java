package com.example.service;

import com.example.dto.RegisterRequest;
import com.example.dto.UserDto;
import com.example.exception.InvalidRequestException;
import com.example.exception.UsernameTakenException;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    // BCrypt берёт только первые 72 байта пароля, а Spring Security 7 на длинном пароле бросает исключение.
    // @Size в запросе считает символы: кириллическая буква в UTF-8 занимает 2 байта
    private static final int BCRYPT_MAX_BYTES = 72;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    public AuthService(AppUserRepository users, PasswordEncoder encoder)
    {
        this.users=users;
        this.encoder=encoder;
    }
    // Порядок: дешёвые проверки, потом медленный BCrypt. existsByUsername ловит обычный случай,
    // а две одновременные регистрации ловит UNIQUE в базе: проверка и вставка — не одна операция
    @Transactional
    public UserDto register(RegisterRequest request)
    {
        if(request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES)
        {
            throw new InvalidRequestException("Пароль длиннее 72 байт: в UTF-8 кириллическая буква занимает 2 байта");
        }
        if(users.existsByUsername(request.username()))
        {
            throw new UsernameTakenException();
        }
        AppUser saved;
        try
        {
            saved = users.saveAndFlush(new AppUser(request.username(), encoder.encode(request.password()), Role.USER));
        }
        catch (DataIntegrityViolationException e)
        {
            // Транзакция после ошибки базы уже помечена на откат: продолжать её нельзя, только выйти с 409
            throw new UsernameTakenException();
        }
        return new UserDto(saved.getId(), saved.getUsername(), saved.getRole());
    }
}
