package com.example.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Критерии хранятся строкой JSON в jsonb. В JSON и обратно их переводит сервис тем же JsonMapper, что и API,
// а не встроенный форматтер Hibernate: правила чтения (enum только по имени, лишние поля пропускаются) одни и те же
@Entity
@Table(name = "saved_searches")
public class SavedSearch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String name;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String criteria;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    protected SavedSearch() {}
    public SavedSearch(Long userId, String name, String criteria, Instant createdAt)
    {
        this.userId=userId;
        this.name=name;
        this.criteria=criteria;
        this.createdAt=createdAt;
    }

    public Long getId()
    {
        return id;
    }
    public Long getUserId()
    {
        return userId;
    }
    public String getName()
    {
        return name;
    }
    public String getCriteria()
    {
        return criteria;
    }
    public Instant getCreatedAt()
    {
        return createdAt;
    }
}
