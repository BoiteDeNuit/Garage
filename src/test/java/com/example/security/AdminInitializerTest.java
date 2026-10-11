package com.example.security;

import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {
    @Mock
    private AppUserRepository repository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsAdminWhenMissing()
    {
        when(repository.insertAdminIfAbsent(eq("boss"), anyString())).thenReturn(1);

        new AdminInitializer(repository, encoder, "boss", "secret").run(null);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertAdminIfAbsent(eq("boss"), hash.capture());
        assertThat(encoder.matches("secret", hash.getValue())).isTrue();
        verify(repository, never()).findByUsername(any());
    }

    @Test
    void updatesPasswordWhenChanged()
    {
        AppUser admin = new AppUser("boss", encoder.encode("old"), Role.ADMIN);
        when(repository.insertAdminIfAbsent(eq("boss"), anyString())).thenReturn(0);
        when(repository.findByUsername("boss")).thenReturn(Optional.of(admin));

        new AdminInitializer(repository, encoder, "boss", "new").run(null);

        verify(repository).save(admin);
        assertThat(encoder.matches("new", admin.getPasswordHash())).isTrue();
    }

    @Test
    void keepsAdminWhenPasswordMatches()
    {
        AppUser admin = new AppUser("boss", encoder.encode("secret"), Role.ADMIN);
        when(repository.insertAdminIfAbsent(eq("boss"), anyString())).thenReturn(0);
        when(repository.findByUsername("boss")).thenReturn(Optional.of(admin));

        new AdminInitializer(repository, encoder, "boss", "secret").run(null);

        verify(repository, never()).save(any());
    }

    // Регистрация открыта: обычный пользователь мог занять логин админа раньше, чем его создали из .env
    @Test
    void refusesToHijackNonAdminUser()
    {
        String hash = encoder.encode("users-own-password");
        AppUser user = new AppUser("boss", hash, Role.USER);
        when(repository.insertAdminIfAbsent(eq("boss"), anyString())).thenReturn(0);
        when(repository.findByUsername("boss")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> new AdminInitializer(repository, encoder, "boss", "from-env").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не является администратором");
        verify(repository, never()).save(any());
        assertThat(user.getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void blankPasswordStopsStartup()
    {
        assertThatThrownBy(() -> new AdminInitializer(repository, encoder, "boss", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
        verifyNoInteractions(repository);
    }
}
