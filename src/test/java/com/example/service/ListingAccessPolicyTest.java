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
            "ARCHIVED, ADMIN, true"
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
            "ADMIN, DELETE, false"
    })
    void actions(String actor, ListingAction action, boolean allowed)
    {
        assertThat(policy.canPerform(action, SELLER, actor(actor))).isEqualTo(allowed);
    }

    // Админ, который сам продаёт машину, — обычный продавец своего объявления
    @Test
    void adminWhoIsSellerCanEdit()
    {
        AppUserPrincipal adminSeller = new AppUserPrincipal(Long.valueOf(1000), "boss", "!", Role.ADMIN);

        assertThat(policy.canPerform(ListingAction.EDIT, SELLER, adminSeller)).isTrue();
        assertThat(policy.canPerform(ListingAction.DELETE, SELLER, adminSeller)).isTrue();
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
            default -> null;
        };
    }
}
