package com.example.dto;
import com.example.model.Car;

public class CarMapper {
    private CarMapper(){}
    public static CarDto toDto(Car car)
    {
        return new CarDto(car.getId(), car.getBrand(), car.getModel(), car.getEngineCode(), car.getHorsePower(), car.getYear());
    }
    public static Car toCar(CarDto dto)
    {
        return Car.builder().brand(dto.brand())
                .model(dto.model())
                .engineCode(dto.engineCode())
                .horsePower(dto.horsePower())
                .year(dto.year())
                .build();
    }
}

