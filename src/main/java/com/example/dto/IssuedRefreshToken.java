package com.example.dto;

import java.time.Duration;

// value — сам токен, он уходит только в куку. В базе его нет, там SHA-256
public record IssuedRefreshToken(Long id, String value, Duration ttl) {
}
