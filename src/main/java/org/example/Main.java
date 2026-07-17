package org.example;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    static void printIds(List<? extends Identifiable> items) {
        for (Identifiable item : items) {
            System.out.println(item.getId());
        }
    }
    public static void main(String[] args) {
        CrudRepository<Car> cars = new InMemoryRepository<>(Car.class,6);
        GarageService garage = new GarageService(cars);
        InMemoryRepository<Owner> owners = new InMemoryRepository<>(Owner.class,10);
        try {
            garage.addCar(Car.builder()
                    .brand("Toyota").model("MarkII")
                    .engineCode("2JZ-GTE").horsePower(270).year(1999)
                    .build());
            garage.addCar(new Car("Toyota", "MarkII", "1JZ-GE", 290, 1999));
            garage.addCar(new Car("Mers", "Mark4I", "B56", 280, 1999));
            garage.addCar(new Car("Subaru", "MARCHOK", "2JZ-GTE", 280, 1999));
            garage.addCar(new Car("Benz", "MAKKR", "1JZ-GTE", 280, 1999));
            garage.addCar(new Car("Toyota", "MAM", "2JZ-GTE", 280, 1999));
            owners.save(new Owner("Yurii","Samara"));
            owners.save(new Owner("Egor","Samara"));
            owners.save(new Owner("Anton","Samara"));

        }
        catch (StorageFullException e)
        {
            System.out.println(e.getMessage());
        }
        System.out.println("Число машин: "+ garage.stats().count());
        try {
            cars.save(new Car("Toyota", "Mark4I", "SR20DET", 280, 1999));
        }
        catch (StorageFullException e)
        {
            System.out.println(e.getMessage());
        }

            boolean deleted = cars.deleteById(3L);
            System.out.println("Машина Удалена: " + (deleted ? "Удалена" : "Не было такой"));
        System.out.println("Число машин: "+ garage.stats().count());
    printIds(cars.findAll());
    printIds(owners.findAll());
    Car defaultCar = cars.findById(1L).orElseThrow(() -> new EntityNotFoundException("Нет такой машины с id 1"));
    cars.findById(999L).ifPresent(c -> System.out.println("Нашли " + c));
    Car withDefault = cars.findById(999L).orElse(defaultCar);
    try {
        Car orBoom = cars.findById(999L).orElseThrow(() -> new EntityNotFoundException("Нет такой машины с id 999"));
        System.out.println("Нашли " + orBoom);
    }
    catch (EntityNotFoundException e)
    {
        System.out.println(e.getMessage());
    }
        List<String> topModels = cars.findAll().stream()
                .filter(c -> c.getHorsePower() > 260)
                .map(Car::getModel)
                .sorted()
                .toList();
        Map<String,List<Car>> groupedByBrands = cars.findAll().stream()
                .collect(Collectors.groupingBy(Car::getBrand));
        Map<String,Long> allBrandCars = cars.findAll().stream()
                .collect(Collectors.groupingBy(Car::getBrand, Collectors.counting()));
        String allModels = cars.findAll().stream()
                .map(Car::getModel)
                .distinct()
                .collect(Collectors.joining(", "));
        boolean anyEngine = cars.findAll().stream()
                .anyMatch(c -> c.getEngineCode().equals("2JZ-GTE"));
        System.out.println("-- Список лучших машин -- ");
        System.out.println(topModels);
        System.out.println("---------------------------");
        System.out.println(garage.stats().averageHp() + " - Средняя мощность машин");
        System.out.println("---------------------------");
        allBrandCars.forEach((brand, n) -> System.out.println(brand + ": " + n));
        System.out.println("Мощнейшая машина: " + garage.stats().strongestModel());
        groupedByBrands.forEach((brand,c) -> System.out.println(brand + ": " + c));
        System.out.println("Все модели: " + allModels);
        System.out.println("Есть ли 2JZ-GTE - " + anyEngine);
        List<Car> carsAbove = cars.findAll().stream()
                .filter(c -> c.getHorsePower() > 270)
                .toList();
        System.out.println("Вместо 999 нашли "+ withDefault);
        boolean unknownEngine = cars.findAll().stream()
                .anyMatch(c -> c.getEngineCode().equals("2JZ-E"));
        System.out.println("Есть ли 2JZ-E - " + unknownEngine);
        for(Car car : carsAbove)
        {
            System.out.println(car);

        }
        System.out.println(garage.findBy(c -> c.getBrand().equals("Toyota")));
        System.out.println(garage.findBy(c -> c.getHorsePower() > 280));
        System.out.println(garage.findBy(c -> c.getEngineCode().equals("2JZ-GTE")));

    }


}