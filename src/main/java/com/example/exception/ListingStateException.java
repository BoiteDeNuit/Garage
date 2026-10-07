package com.example.exception;

import com.example.model.ListingStatus;

// Конфликт с текущим состоянием объявления: переход запрещён или не хватает данных. Отдаётся как 409
public class ListingStateException extends RuntimeException {
    private ListingStateException(String message)
    {
        super(message);
    }
    public static ListingStateException transition(ListingStatus from, ListingStatus to)
    {
        return new ListingStateException("Нельзя перевести объявление из " + from + " в " + to);
    }
    public static ListingStateException priceRequired()
    {
        return new ListingStateException("Для публикации нужна цена");
    }
    public static ListingStateException cityRequired()
    {
        return new ListingStateException("Для публикации нужен город");
    }
}
