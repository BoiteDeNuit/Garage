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
    public static ListingStateException soldIsFinal()
    {
        return new ListingStateException("Проданное объявление менять нельзя");
    }
    public static ListingStateException activeNeedsPriceAndCity()
    {
        return new ListingStateException("У опубликованного объявления должны быть цена и город");
    }
    public static ListingStateException staleVersion(long actual)
    {
        return new ListingStateException("Объявление уже изменили, актуальная версия " + actual + ". Обновите и повторите");
    }
    public static ListingStateException notDraft()
    {
        return new ListingStateException("Удалить можно только черновик. Опубликованное объявление снимите в архив");
    }
    public static ListingStateException tooManyPhotos(int max)
    {
        return new ListingStateException("Не больше " + max + " фото на объявление");
    }
}
