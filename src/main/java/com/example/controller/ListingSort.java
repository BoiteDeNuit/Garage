package com.example.controller;

import com.example.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Сортировать можно только по этим полям. Без списка Spring Data выполнил бы и sort=seller.passwordHash.
// id в конце нужен для стабильного порядка: при равных датах строки иначе прыгают между страницами
public final class ListingSort {
    private static final Set<String> ALLOWED = Set.of("publishedAt", "createdAt", "price", "year", "mileageKm", "horsePower", "id");
    private ListingSort(){}
    public static Pageable forPublicFeed(Pageable pageable)
    {
        return checked(pageable, Sort.by(Sort.Direction.DESC, "publishedAt"));
    }
    public static Pageable forAdminList(Pageable pageable)
    {
        return checked(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    private static Pageable checked(Pageable pageable, Sort defaultSort)
    {
        // Смещение страницы считается в int: page=30000000 при size=100 переполнил бы его и дал 500
        if((long) pageable.getPageNumber() * pageable.getPageSize() > Integer.MAX_VALUE)
        {
            throw new InvalidRequestException("Слишком большой номер страницы");
        }
        Sort source = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        List<Sort.Order> orders = new ArrayList<>();
        for(Sort.Order order : source)
        {
            if(!ALLOWED.contains(order.getProperty()))
            {
                throw new InvalidRequestException("Нельзя сортировать по полю: " + order.getProperty());
            }
            // Порядок собирается заново: флаг ignorecase у числа или даты превратился бы в UPPER(price) и 500
            orders.add(new Sort.Order(order.getDirection(), order.getProperty()));
        }
        Sort sort = Sort.by(orders);
        if(sort.getOrderFor("id") == null)
        {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
