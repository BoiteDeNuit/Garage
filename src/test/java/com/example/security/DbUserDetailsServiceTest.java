package com.example.security;

import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbUserDetailsServiceTest {
    @Mock
    private AppUserRepository repository;
    @InjectMocks
    private DbUserDetailsService service;

    @Test
    void returnsPrincipalWithId()
    {
        when(repository.findByUsername("seller")).thenReturn(Optional.of(user("seller", Role.USER)));

        AppUserPrincipal principal = (AppUserPrincipal) service.loadUserByUsername("seller");

        assertThat(principal.getId()).isEqualTo(42L);
        assertThat(principal.getUsername()).isEqualTo("seller");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void toStringHidesPasswordHash()
    {
        when(repository.findByUsername("boss")).thenReturn(Optional.of(user("boss", Role.ADMIN)));

        String text = service.loadUserByUsername("boss").toString();

        assertThat(text).contains("boss").contains("ADMIN").doesNotContain("$2a$10$hash");
    }

    @Test
    void eraseCredentialsRemovesHash()
    {
        when(repository.findByUsername("seller")).thenReturn(Optional.of(user("seller", Role.USER)));
        AppUserPrincipal principal = (AppUserPrincipal) service.loadUserByUsername("seller");

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getId()).isEqualTo(42L);
    }

    @Test
    void unknownUserIsRejected()
    {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    private AppUser user(String username, Role role)
    {
        AppUser user = new AppUser(username, "$2a$10$hash", role);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }
}
