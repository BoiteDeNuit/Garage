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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.userId = :userId and t.revokedAt is null")
    int revokeAllOfUser(@Param("userId") Long userId, @Param("reason") RevokeReason reason, @Param("now") Instant now);

    // Пачка истёкших для уборки (RefreshTokenCleanup). LIMIT в DELETE Postgres не умеет, поэтому через подзапрос по id.
    // Строки, на которые ссылается replaced_by_id, тоже удаляются: ссылка станет NULL (ON DELETE SET NULL)
    @Modifying
    @Query(value = "delete from refresh_tokens where id in (select id from refresh_tokens where expires_at < :before order by id limit :batch)",
            nativeQuery = true)
    int deleteExpiredBatch(@Param("before") Instant before, @Param("batch") int batch);
}
