package com.example.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// Сам токен не хранится, только его SHA-256. Пользователь — id, а не связь: токену нужен только id,
// а пользователь при обмене всё равно читается заново, вместе с блокировкой и ролью
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private UUID familyId;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;
    @Enumerated(EnumType.STRING)
    private RevokeReason revokeReason;
    private Long replacedById;
    protected RefreshToken() {}
    public RefreshToken(Long userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt)
    {
        this.userId=userId;
        this.familyId=familyId;
        this.tokenHash=tokenHash;
        this.createdAt=createdAt;
        this.expiresAt=expiresAt;
    }
    public void replacedBy(Long nextId)
    {
        this.replacedById=nextId;
    }
    public Long getId()
    {
        return id;
    }
    public Long getUserId()
    {
        return userId;
    }
    public UUID getFamilyId()
    {
        return familyId;
    }
    public Instant getExpiresAt()
    {
        return expiresAt;
    }
    public RevokeReason getRevokeReason()
    {
        return revokeReason;
    }
}
