package com.example.service;

import com.example.model.ListingAction;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.security.AppUserPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

// Кто что видит и кто что меняет. Защита от IDOR: id объявления в URL ещё не значит, что его можно трогать.
// Политика получает уже загруженные данные и в базу не ходит, поэтому одинаково работает и с карточкой из кэша
@Component
public class ListingAccessPolicy {
    // Опубликованные и проданные видят все, черновики и архив только продавец и админ.
    // Ждущее проверки и отклонённое видит ещё и модератор: ему их проверять
    private static final Set<ListingStatus> PUBLIC_STATUSES = EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD);
    private static final Set<ListingStatus> REVIEW_STATUSES = EnumSet.of(ListingStatus.PENDING_REVIEW, ListingStatus.REJECTED);

    public Set<ListingStatus> publicStatuses()
    {
        return PUBLIC_STATUSES;
    }
    // id сравниваются через equals: Long больше 127 из разных мест — разные объекты, == дал бы false
    public boolean canView(ListingStatus status, Long sellerId, @Nullable AppUserPrincipal viewer)
    {
        if(PUBLIC_STATUSES.contains(status))
        {
            return true;
        }
        if(viewer != null && viewer.getRole() == Role.MODERATOR && REVIEW_STATUSES.contains(status))
        {
            return true;
        }
        return viewer != null && (viewer.getId().equals(sellerId) || viewer.getRole() == Role.ADMIN);
    }
    // В журнале id модераторов и причины отказов. Модератору он нужен, чтобы видеть прошлые отказы,
    // но только у того, что на проверке или отклонено: чужие черновики и архив ему не показываем
    public boolean canSeeHistory(ListingStatus status, Long sellerId, @Nullable AppUserPrincipal viewer)
    {
        if(viewer != null && viewer.getRole() == Role.MODERATOR && REVIEW_STATUSES.contains(status))
        {
            return true;
        }
        return viewer != null && (viewer.getId().equals(sellerId) || viewer.getRole() == Role.ADMIN);
    }
    // Админ здесь модератор, а не соавтор: снять объявление может, править и продавать за продавца нет
    public boolean canPerform(ListingAction action, Long sellerId, AppUserPrincipal actor)
    {
        boolean isSeller = sellerId.equals(actor.getId());
        return switch (action)
        {
            case EDIT, PUBLISH, MARK_SOLD, DELETE -> isSeller;
            case ARCHIVE -> isSeller || actor.getRole() == Role.ADMIN;
            // Своё объявление проверяет другой модератор, даже если продавец сам модератор или админ
            case MODERATE -> !isSeller && (actor.getRole() == Role.MODERATOR || actor.getRole() == Role.ADMIN);
        };
    }
}
