package com.example.service;

import com.example.dto.IssuedRefreshToken;
import com.example.dto.RotatedSession;
import com.example.exception.InvalidRefreshTokenException;
import com.example.model.AppUser;
import com.example.model.RefreshToken;
import com.example.model.RevokeReason;
import com.example.repository.AppUserRepository;
import com.example.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// Долгая сессия без долгого access-токена. Access живёт 30 минут, refresh — 14 дней и меняется при каждом обмене.
// Токен — 32 случайных байта: SHA-256 без соли и BCrypt тут хватает, перебирать нечего, в отличие от пароля
@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository tokens;
    private final AppUserRepository users;
    private final Duration ttl;
    private final Clock clock;
    public RefreshTokenService(RefreshTokenRepository tokens,
                               AppUserRepository users,
                               @Value("${auth.refresh.ttl:14d}") Duration ttl,
                               Clock clock)
    {
        this.tokens=tokens;
        this.users=users;
        this.ttl=ttl;
        this.clock=clock;
    }
    // Вход начинает новую семью
    @Transactional
    public IssuedRefreshToken issue(Long userId)
    {
        return create(userId, UUID.randomUUID(), Instant.now(clock));
    }
    // Старый токен гасится, новый выдаётся в той же семье. Исключения не откатывают транзакцию:
    // отзыв семьи при повторе и при блокировке должен остаться в базе, хотя ответ — 401 или 403
    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, LockedException.class})
    public RotatedSession rotate(String raw)
    {
        String hash = hash(raw);
        Instant now = Instant.now(clock);
        RefreshToken presented = tokens.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        AppUser user = users.findById(presented.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        // Блокировка — до обмена: иначе предъявленный токен погасился бы как обменянный, и после разблокировки
        // старый токен из браузера сошёл бы за украденный
        if(user.isBanned())
        {
            tokens.revokeFamily(presented.getFamilyId(), RevokeReason.BAN, now);
            throw new LockedException("Аккаунт заблокирован");
        }
        if(tokens.revokeIfActive(hash, RevokeReason.ROTATED, now) == 0)
        {
            // Токен уже обменяли, а он пришёл снова: у кого-то копия. Кто из двоих настоящий, не узнать — гасим обоих.
            // Перечитываем: UPDATE выше очистил контекст, а токен мог обменять параллельный запрос
            tokens.findByTokenHash(hash)
                    .filter(token -> token.getRevokeReason() == RevokeReason.ROTATED)
                    .ifPresent(token -> revokeReused(token, now));
            throw new InvalidRefreshTokenException();
        }
        IssuedRefreshToken next = create(user.getId(), presented.getFamilyId(), now);
        tokens.findById(presented.getId()).orElseThrow(InvalidRefreshTokenException::new).replacedBy(next.id());
        return new RotatedSession(user.getUsername(), List.of("ROLE_" + user.getRole().name()), user.getTokenVersion(), next);
    }
    // Выход с этого устройства: гасится семья, к которой относится кука. Неизвестная кука — не ошибка, выйти и так вышли.
    // Access-токен этого входа доживёт свои 30 минут: клиент его просто выбрасывает
    @Transactional
    public void logout(String raw)
    {
        Instant now = Instant.now(clock);
        tokens.findByTokenHash(hash(raw)).ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, now));
    }
    // Выход отовсюду: все refresh-токены и все выданные access-токены (версия в токене станет меньше, чем в базе)
    @Transactional
    public void logoutEverywhere(Long userId)
    {
        AppUser user = users.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        user.revokeTokens();
        tokens.revokeAllOfUser(userId, RevokeReason.LOGOUT, Instant.now(clock));
    }
    private void revokeReused(RefreshToken token, Instant now)
    {
        int revoked = tokens.revokeFamily(token.getFamilyId(), RevokeReason.REUSE, now);
        log.warn("Обменянный refresh-токен пользователя {} предъявлен повторно: семья {} отозвана, живых токенов было {}",
                token.getUserId(), token.getFamilyId(), revoked);
    }
    private IssuedRefreshToken create(Long userId, UUID familyId, Instant now)
    {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = tokens.save(new RefreshToken(userId, familyId, hash(raw), now, now.plus(ttl)));
        return new IssuedRefreshToken(token.getId(), raw, ttl);
    }
    static String hash(String raw)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("В JVM нет SHA-256", e);
        }
    }
}
