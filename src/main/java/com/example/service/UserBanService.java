package com.example.service;

import com.example.dto.UserBanDto;
import com.example.event.ListingCardsChanged;
import com.example.exception.ConflictException;
import com.example.exception.EntityNotFoundException;
import com.example.exception.InvalidRequestException;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Блокировка действует сразу: пользователь читается из базы на каждый запрос (JwtAuthFilter), токен ждать не нужно.
// Объявления уходят с витрины тем же коммитом. После разблокировки они остаются в архиве и отклонёнными:
// продавец сам решит, что публиковать снова.
// Узкое окно: публикация черновика, начатая до блокировки и закоммиченная после, останется в ленте — её снимет админ
@Service
public class UserBanService {
    private final AppUserRepository users;
    private final ListingRepository listings;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public UserBanService(AppUserRepository users, ListingRepository listings, ApplicationEventPublisher events, Clock clock)
    {
        this.users=users;
        this.listings=listings;
        this.events=events;
        this.clock=clock;
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserBanDto ban(String username, String reason, AppUserPrincipal admin)
    {
        // @Size считает и пробелы по краям, длину проверяем после обрезки
        String trimmed = reason.trim();
        if(trimmed.length() < 5 || trimmed.length() > 500)
        {
            throw new InvalidRequestException("Причина блокировки от 5 до 500 символов");
        }
        AppUser user = find(username);
        if(user.getRole() == Role.ADMIN)
        {
            throw new ConflictException("Администратора заблокировать нельзя");
        }
        if(user.isBanned())
        {
            throw new ConflictException("Пользователь уже заблокирован");
        }
        Instant now = Instant.now(clock);
        user.ban(trimmed, now);
        List<Long> hidden = listings.hideAllOfSeller(user.getId(), admin.getId(), trimmed, now);
        events.publishEvent(new ListingCardsChanged(hidden));
        return new UserBanDto(user.getUsername(), user.getBannedAt(), user.getBanReason(), hidden.size());
    }
    // Повтор и снятие с незаблокированного — не ошибка: результат тот же, пользователь не заблокирован
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void unban(String username)
    {
        find(username).unban();
    }
    private AppUser find(String username)
    {
        return users.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь " + username + " не найден"));
    }
}
