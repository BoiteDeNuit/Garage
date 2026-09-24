package com.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
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
}
