package com.example.security;

import com.example.model.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

// Свой принципал вместо User из Spring: контроллерам нужен id пользователя, а не только логин.
// Класс, а не record: toString у record вывел бы хэш пароля в логи
public class AppUserPrincipal implements UserDetails, CredentialsContainer {
    private final Long id;
    private final String username;
    private String passwordHash;
    private final Role role;
    public AppUserPrincipal(Long id, String username, String passwordHash, Role role)
    {
        this.id=id;
        this.username=username;
        this.passwordHash=passwordHash;
        this.role=role;
    }
    public Long getId()
    {
        return id;
    }
    public Role getRole()
    {
        return role;
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override
    public String getPassword()
    {
        return passwordHash;
    }
    @Override
    public String getUsername()
    {
        return username;
    }
    // После проверки пароля Spring стирает хэш, чтобы он не жил в SecurityContext
    @Override
    public void eraseCredentials()
    {
        this.passwordHash=null;
    }
    @Override
    public String toString()
    {
        return "Пользователь {id=" + id + ", " + username + ", " + role + "}";
    }
}
