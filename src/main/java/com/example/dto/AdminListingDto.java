package com.example.dto;

// Логин продавца нужен только админу. В публичной карточке его нет
public record AdminListingDto(ListingDto listing, String sellerUsername) {
}
