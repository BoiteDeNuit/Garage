package com.example.dto;

import java.util.List;

// Страница ленты по курсору. Общего числа нет: count на сотнях тысяч строк стоил дороже самой страницы.
// nextCursor = null — дальше объявлений нет
public record FeedPage(List<ListingDto> items, String nextCursor) {
}
