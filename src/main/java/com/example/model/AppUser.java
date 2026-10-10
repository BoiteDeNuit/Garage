package com.example.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

// UPDATE только изменённых колонок. Без этого Hibernate пишет строку целиком из того, что прочитал:
// смена роли, начатая до блокировки и закоммиченная после, вернула бы banned_at = null и молча сняла блокировку
@Entity
@Table(name = "users")
@DynamicUpdate
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    private Instant bannedAt;
    private String banReason;
    protected AppUser () {}
    public AppUser(String username, String passwordHash, Role role)
    {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    public void changePassword(String passwordHash)
    {
        this.passwordHash = passwordHash;
    }
    public void changeRole(Role role)
    {
        this.role = role;
    }
    public void ban(String reason, Instant now)
    {
        this.bannedAt = now;
        this.banReason = reason;
    }
    public void unban()
    {
        this.bannedAt = null;
        this.banReason = null;
    }
    public boolean isBanned()
    {
        return bannedAt != null;
    }

    public Long getId() {
        return id;
    }
    public String getUsername()
    {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getBannedAt()
    {
        return bannedAt;
    }

    public String getBanReason()
    {
        return banReason;
    }
}
