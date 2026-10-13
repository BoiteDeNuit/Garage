package com.example.service;

import com.example.model.ListingAction;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// id от 1000: Long.valueOf вне кэша -128..127 даёт разные объекты, и сравнение через == здесь бы сломалось
class ListingAccessPolicyTest {
    private static final Long SELLER = Long.valueOf(1000);
    private final ListingAccessPolicy policy = new ListingAccessPolicy();

    @ParameterizedTest(name = "{0} видит {1}: {2}")
    @CsvSource({
            "DRAFT, ANONYMOUS, false",
            "DRAFT, SELLER, true",
            "DRAFT, STRANGER, false",
            "DRAFT, ADMIN, true",
            "ACTIVE, ANONYMOUS, true",
            "ACTIVE, SELLER, true",
            "ACTIVE, STRANGER, true",
            "ACTIVE, ADMIN, true",
            "SOLD, ANONYMOUS, true",
            "SOLD, SELLER, true",
            "SOLD, STRANGER, true",
            "SOLD, ADMIN, true",
            "ARCHIVED, ANONYMOUS, false",
            "ARCHIVED, SELLER, true",
            "ARCHIVED, STRANGER, false",
            "ARCHIVED, ADMIN, true",
            "PENDING_REVIEW, ANONYMOUS, false",
            "PENDING_REVIEW, SELLER, true",
            "PENDING_REVIEW, STRANGER, false",
            "PENDING_REVIEW, ADMIN, true",
            "REJECTED, ANONYMOUS, false",
            "REJECTED, SELLER, true",
            "REJECTED, STRANGER, false",
            "REJECTED, ADMIN, true",
            "PENDING_REVIEW, MODERATOR, true",
            "REJECTED, MODERATOR, true",
            "DRAFT, MODERATOR, false",
            "ARCHIVED, MODERATOR, false",
            "ACTIVE, MODERATOR, true"
    })
    void visibility(ListingStatus status, String viewer, boolean visible)
    {
        assertThat(policy.canView(status, SELLER, actor(viewer))).isEqualTo(visible);
    }

    @ParameterizedTest(name = "{0} может {1}: {2}")
    @CsvSource({
            "SELLER, EDIT, true",
            "SELLER, PUBLISH, true",
            "SELLER, MARK_SOLD, true",
            "SELLER, ARCHIVE, true",
            "SELLER, DELETE, true",
            "STRANGER, EDIT, false",
            "STRANGER, PUBLISH, false",
            "STRANGER, MARK_SOLD, false",
            "STRANGER, ARCHIVE, false",
            "STRANGER, DELETE, false",
            "ADMIN, EDIT, false",
            "ADMIN, PUBLISH, false",
            "ADMIN, MARK_SOLD, false",
            "ADMIN, ARCHIVE, true",
            "ADMIN, DELETE, false",
            "ADMIN, MODERATE, true",
            "MODERATOR, MODERATE, true",
            "MODERATOR, EDIT, false",
            "MODERATOR, ARCHIVE, false",
            "SELLER, MODERATE, false",
            "STRANGER, MODERATE, false"
    })
    void actions(String actor, ListingAction action, boolean allowed)
    {
        assertThat(policy.canPerform(action, SELLER, actor(actor))).isEqualTo(allowed);
    }

    // Админ, который сам продаёт машину, — обычный продавец своего объявления
    // Модератор, который сам продаёт, своё объявление не проверяет
    @Test
    void moderatorCannotModerateOwnListing()
    {
        AppUserPrincipal moderatorSeller = new AppUserPrincipal(SELLER, "moder", "!", Role.MODERATOR);

        assertThat(policy.canPerform(ListingAction.MODERATE, SELLER, moderatorSeller)).isFalse();
    }

    @Test
    void adminWhoIsSellerCanEdit()
    {
        AppUserPrincipal adminSeller = new AppUserPrincipal(Long.valueOf(1000), "boss", "!", Role.ADMIN);

        assertThat(policy.canPerform(ListingAction.EDIT, SELLER, adminSeller)).isTrue();
        assertThat(policy.canPerform(ListingAction.DELETE, SELLER, adminSeller)).isTrue();
    }

    // В журнале id модераторов и причины отказов: продавцу и админу всегда, модератору — пока объявление на проверке
    // или отклонено. Чужой черновик и архив модератору не нужны
    @ParameterizedTest(name = "{1} видит журнал {0}: {2}")
    @CsvSource({
            "ACTIVE, SELLER, true",
            "ACTIVE, ADMIN, true",
            "ACTIVE, STRANGER, false",
            "ACTIVE, ANONYMOUS, false",
            "DRAFT, SELLER, true",
            "DRAFT, ADMIN, true",
            "DRAFT, MODERATOR, false",
            "ARCHIVED, MODERATOR, false",
            "ACTIVE, MODERATOR, false",
            "SOLD, MODERATOR, false",
            "PENDING_REVIEW, MODERATOR, true",
            "REJECTED, MODERATOR, true",
            "PENDING_REVIEW, STRANGER, false"
    })
    void historyIsForSellerAdminAndModeratorUnderReview(ListingStatus status, String viewer, boolean visible)
    {
        assertThat(policy.canSeeHistory(status, SELLER, actor(viewer))).isEqualTo(visible);
    }

    @Test
    void publicStatusesAreActiveAndSold()
    {
        assertThat(policy.publicStatuses()).containsExactlyInAnyOrder(ListingStatus.ACTIVE, ListingStatus.SOLD);
    }

    private AppUserPrincipal actor(String who)
    {
        return switch (who)
        {
            case "SELLER" -> new AppUserPrincipal(Long.valueOf(1000), "seller", "!", Role.USER);
            case "STRANGER" -> new AppUserPrincipal(Long.valueOf(1001), "stranger", "!", Role.USER);
            case "ADMIN" -> new AppUserPrincipal(Long.valueOf(1), "boss", "!", Role.ADMIN);
            case "MODERATOR" -> new AppUserPrincipal(Long.valueOf(2), "moder", "!", Role.MODERATOR);
            default -> null;
        };
    }
}
