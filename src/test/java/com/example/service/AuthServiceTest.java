package com.example.service;

import com.example.dto.RegisterRequest;
import com.example.dto.UserDto;
import com.example.exception.InvalidRequestException;
import com.example.exception.UsernameTakenException;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AppUserRepository users;
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
    private AuthService service;

    @BeforeEach
    void setUp()
    {
        service = new AuthService(users, encoder);
    }

    @Test
    void savesUserWithHashAndRoleUser()
    {
        when(users.existsByUsername("seller_42")).thenReturn(false);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> {
            AppUser user = inv.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 5L);
            return user;
        });

        UserDto created = service.register(new RegisterRequest("seller_42", "correct-horse-battery"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-battery").startsWith("$2a$");
        assertThat(encoder.matches("correct-horse-battery", saved.getPasswordHash())).isTrue();
        assertThat(created).isEqualTo(new UserDto(5L, "seller_42", Role.USER));
    }

    @Test
    void takenUsernameIsRejectedBeforeHashing()
    {
        when(users.existsByUsername("seller_42")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("seller_42", "correct-horse-battery")))
                .isInstanceOf(UsernameTakenException.class)
                .hasMessage("Логин уже занят");
        verify(encoder, never()).encode(any());
        verify(users, never()).saveAndFlush(any());
    }

    // Обе регистрации прошли existsByUsername, вторую останавливает UNIQUE в базе
    @Test
    void uniqueViolationBecomesUsernameTaken()
    {
        when(users.existsByUsername("seller_42")).thenReturn(false);
        when(users.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("seller_42", "correct-horse-battery")))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void cyrillicPasswordOver72BytesIsRejectedBeforeDatabaseAndHashing()
    {
        String password = "п".repeat(40);

        assertThatThrownBy(() -> service.register(new RegisterRequest("seller_42", password)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("72 байт");
        verifyNoInteractions(users);
        verify(encoder, never()).encode(any());
    }

    @Test
    void exactly72AsciiBytesAreAccepted()
    {
        when(users.existsByUsername("seller_42")).thenReturn(false);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterRequest("seller_42", "a".repeat(72)));

        verify(users).saveAndFlush(any(AppUser.class));
    }
}
