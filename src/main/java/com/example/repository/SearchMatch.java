package com.example.repository;

// Сохранённый поиск, под который подошло объявление, и его владелец
public interface SearchMatch {
    Long getSearchId();
    Long getUserId();
}
