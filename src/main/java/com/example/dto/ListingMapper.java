package com.example.dto;

import com.example.model.Listing;
import com.example.model.ListingDetails;

public class ListingMapper {
    private ListingMapper(){}
    // getSeller().getId() не инициализирует ленивый прокси: id у него есть и так
    public static ListingDto toDto(Listing listing)
    {
        return new ListingDto(listing.getId(), listing.getSeller().getId(), listing.getStatus(),
                listing.getBrand(), listing.getModel(), listing.getEngineCode(), listing.getHorsePower(), listing.getYear(),
                listing.getMileageKm(), listing.getPrice(), listing.getCity(), listing.getDescription(),
                listing.getCreatedAt(), listing.getUpdatedAt(), listing.getPublishedAt(), listing.getVersion());
    }
    // Продавец должен быть уже загружен (@EntityGraph), иначе getUsername() даст по запросу на каждую строку
    public static AdminListingDto toAdminDto(Listing listing)
    {
        return new AdminListingDto(toDto(listing), listing.getSeller().getUsername());
    }
    public static ListingDetails toDetails(ListingRequest request)
    {
        return new ListingDetails(request.brand(), request.model(), request.engineCode(),
                request.horsePower(), request.year(), request.mileageKm(),
                request.price(), request.city(), request.description());
    }
    public static ListingDetails toDetails(ListingUpdateRequest request)
    {
        return new ListingDetails(request.brand(), request.model(), request.engineCode(),
                request.horsePower(), request.year(), request.mileageKm(),
                request.price(), request.city(), request.description());
    }
}
