package com.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.ttl-minutes:30}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60 * 1000;
    }

    // tv — версия токенов пользователя на момент выдачи. Выход отовсюду и блокировка её поднимают,
    // и JwtAuthFilter отвергает токен, не дожидаясь срока. По времени выдачи так не сделать: iat в секундах
    public String generateToken(String username, Collection<String> roles, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parse(token).getPayload().getSubject();
    }

    // Токены, выданные до появления версии, считаются версией 0
    public int extractTokenVersion(String token) {
        Integer version = parse(token).getPayload().get("tv", Integer.class);
        return version == null ? 0 : version;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return parse(token).getPayload().get("roles", List.class);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}