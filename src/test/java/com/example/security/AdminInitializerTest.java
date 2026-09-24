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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {
    @Mock
    private AppUserRepository repository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsAdminWhenMissing()
    {
        when(repository.findByUsername("boss")).thenReturn(Optional.empty());

        new AdminInitializer(repository, encoder, "boss", "secret").run(null);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("boss");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(encoder.matches("secret", saved.getPasswordHash())).isTrue();
    }

    @Test
    void updatesPasswordWhenChanged()
    {
        AppUser admin = new AppUser("boss", encoder.encode("old"), Role.ADMIN);
        when(repository.findByUsername("boss")).thenReturn(Optional.of(admin));

        new AdminInitializer(repository, encoder, "boss", "new").run(null);

        verify(repository).save(admin);
        assertThat(encoder.matches("new", admin.getPasswordHash())).isTrue();
    }

    @Test
    void keepsAdminWhenPasswordMatches()
    {
        AppUser admin = new AppUser("boss", encoder.encode("secret"), Role.ADMIN);
        when(repository.findByUsername("boss")).thenReturn(Optional.of(admin));

        new AdminInitializer(repository, encoder, "boss", "secret").run(null);

        verify(repository, never()).save(any());
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
