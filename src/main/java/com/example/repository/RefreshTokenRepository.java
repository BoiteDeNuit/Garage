package com.example.repository;

import com.example.model.RefreshToken;
import com.example.model.RevokeReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Обмен гасит токен одним условным UPDATE: из двух одновременных обменов одного токена строку изменит один.
    // SELECT «жив ли» и потом UPDATE пропустили бы обоих, и из одного токена вышло бы два
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.tokenHash = :hash and t.revokedAt is null and t.expiresAt > :now")
    int revokeIfActive(@Param("hash") String hash, @Param("reason") RevokeReason reason, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") RevokeReason reason, @Param("now") Instant now);
}
