package com.example.controller;

import com.example.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingSortTest {

    @Test
    void defaultIsNewestFirst()
    {
        Pageable result = ListingSort.forPublicFeed(PageRequest.of(2, 30));

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(30);
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
    }

    @Test
    void adminDefaultIsNewestCreatedFirst()
    {
        Pageable result = ListingSort.forAdminList(PageRequest.of(0, 20));

        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void ownerDefaultIsNewestCreatedFirst()
    {
        Pageable result = ListingSort.forOwnerList(PageRequest.of(0, 20));

        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void idIsNotAddedTwice()
    {
        Pageable result = ListingSort.forPublicFeed(PageRequest.of(0, 20, Sort.by(Sort.Order.asc("id"))));

        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Order.asc("id")));
    }

    @Test
    void fieldOutsideWhitelistIsRejected()
    {
        assertThatThrownBy(() -> ListingSort.forPublicFeed(PageRequest.of(0, 20, Sort.by("description"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Нельзя сортировать по полю: description");
    }

    @Test
    void ignoreCaseFlagIsDropped()
    {
        Pageable result = ListingSort.forPublicFeed(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("price").ignoreCase())));

        assertThat(result.getSort().getOrderFor("price").isIgnoreCase()).isFalse();
    }

    @Test
    void hugePageNumberIsRejected()
    {
        assertThatThrownBy(() -> ListingSort.forPublicFeed(PageRequest.of(30_000_000, 100)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Слишком большой номер страницы");
    }

    @Test
    void nestedPathIsRejected()
    {
        assertThatThrownBy(() -> ListingSort.forPublicFeed(PageRequest.of(0, 20, Sort.by("seller.passwordHash"))))
                .isInstanceOf(InvalidRequestException.class);
    }
}
