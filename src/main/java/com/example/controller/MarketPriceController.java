package com.example.controller;

import com.example.dto.MarketPriceDto;
import com.example.service.MarketPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Объявления", description = "Продажа машин")
@RestController
@RequestMapping("/api/listings")
public class MarketPriceController {
    private final MarketPriceService marketPrices;
    public MarketPriceController(MarketPriceService marketPrices) { this.marketPrices=marketPrices; }
    @Operation(summary = "Цена по рынку", description = "Квартили цен опубликованных объявлений той же марки, модели и года. "
            + "Пересчитываются раз в час, меньше пяти похожих — NOT_ENOUGH_DATA")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не опубликовано")
    @GetMapping("/{id}/market-price")
    public MarketPriceDto marketPrice(@PathVariable Long id)
    {
        return marketPrices.estimate(id);
    }
}
