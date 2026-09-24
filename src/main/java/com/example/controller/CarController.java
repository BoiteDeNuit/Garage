package com.example.controller;

import com.example.dto.CarDto;
import com.example.dto.CarPriceDto;
import com.example.dto.GarageStats;
import com.example.service.GarageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Машины", description = "Учёт машин")
@RestController
@RequestMapping("/api/cars")
public class CarController {
    private final GarageService garage;
    public CarController(GarageService garage) { this.garage=garage; }
    @Operation(summary = "Список машин", description = "Фильтр по бренду и пагинация")
    @GetMapping
    public Page<CarDto> all(@RequestParam(required = false) String brand,
                            @ParameterObject @PageableDefault(size = 20,sort = "id") Pageable pageable) {
        if (brand == null)
        {
            return garage.findAll(pageable);
        }
        return garage.findByBrand(brand,pageable);
    }
    @Operation(summary = "Машина по id")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Машина не найдена")
    @GetMapping ("/{id}")
    public CarDto one(@PathVariable Long id)
    {
        return garage.getCar(id);
    }
    @Operation(summary = "Добавить машину", description = "Только ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "400", description = "Ошибка в данных")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto create(@Valid @RequestBody CarDto dto)
    {
        return garage.addCar(dto);
    }
    @Operation(summary = "Удалить машину", description = "Только ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "404", description = "Машина не найдена")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void carDelete(@PathVariable Long id){
            garage.deleteCar(id);
    }
    @Operation(summary = "Статистика гаража")
    @GetMapping("/stats")
    public GarageStats stats()
    {
        return garage.stats();
    }
    @Operation(summary = "Цена в валюте", description = "Курс ЦБ на сегодня")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Неизвестная валюта")
    @ApiResponse(responseCode = "404", description = "Нет машины или цены")
    @ApiResponse(responseCode = "503", description = "ЦБ недоступен")
    @GetMapping("/{id}/price")
    public CarPriceDto price(@PathVariable Long id, @RequestParam(defaultValue = "USD") String currency)
    {
        return garage.priceIn(id,currency);
    }

}
