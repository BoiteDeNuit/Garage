package com.example.controller;

import com.example.dto.ModelSuggestion;
import com.example.exception.InvalidRequestException;
import com.example.service.CarModelCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Справочник", description = "Марки и модели для подсказок в поиске")
@RestController
@RequestMapping("/api/models")
public class CarModelController {
    private final CarModelCatalog catalog;
    public CarModelController(CarModelCatalog catalog) { this.catalog=catalog; }
    @Operation(summary = "Подсказки марок и моделей", description = "Прощают опечатки и недописанное слово: toyta найдёт Toyota, camr — Camry. "
            + "Только марки и модели, которые уже публиковали. Самые похожие первыми")
    @ApiResponse(responseCode = "200", description = "OK, пустой список — ничего похожего")
    @ApiResponse(responseCode = "400", description = "Запрос не от 2 до 50 символов или limit не от 1 до 20")
    @GetMapping
    public List<ModelSuggestion> suggest(@Parameter(description = "Что ввёл пользователь", example = "toyta camr") @RequestParam String q,
                                         @Parameter(description = "Сколько подсказок, от 1 до 20") @RequestParam(defaultValue = "10") int limit)
    {
        // Из одной буквы почти не набрать триграмм: похожим окажется что попало
        String text = q.trim();
        if(text.length() < 2 || text.length() > 50)
        {
            throw new InvalidRequestException("Запрос подсказки от 2 до 50 символов");
        }
        if(limit < 1 || limit > 20)
        {
            throw new InvalidRequestException("Подсказок от 1 до 20");
        }
        return catalog.suggest(text, limit);
    }
}
