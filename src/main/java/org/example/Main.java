package org.example;

import org.apache.commons.lang3.StringUtils;
import org.example.concurrency.AtomicCounter;
import org.example.concurrency.Counter;
import org.example.concurrency.SyncCounter;
import org.example.exception.EntityNotFoundException;
import org.example.exception.StorageFullException;
import org.example.model.Car;
import org.example.model.GarageStats;
import org.example.repository.CrudRepository;
import org.example.model.Identifiable;
import org.example.model.Owner;
import org.example.repository.InMemoryRepository;
import org.example.service.GarageService;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Main {
    private static CrudRepository<Car> cars;
    private static InMemoryRepository<Owner> owners;
    private static GarageService garage;

    static void printIds(List<? extends Identifiable> items) {
        for (Identifiable item : items) {
            System.out.println(item.getId());
        }
    }

    static int runExperiment(Runnable increment, java.util.function.IntSupplier result) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 100_000; i++) {
                    increment.run();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        return result.getAsInt();
    }

    static void counterDemo() throws InterruptedException {
        Counter plain = new Counter();
        SyncCounter sync = new SyncCounter();
        AtomicCounter atomic = new AtomicCounter();
        System.out.println("Сломанный:    " + runExperiment(plain::increment, plain::getValue));
        System.out.println("Synchronized: " + runExperiment(sync::increment, sync::getValue));
        System.out.println("Atomic:       " + runExperiment(atomic::increment, atomic::getValue));
    }

    static void seedGarage() {
        try {
            garage.addCar(Car.builder()
                    .brand("Toyota").model("MarkII")
                    .engineCode("2JZ-GTE").horsePower(270).year(1999)
                    .build());
            garage.addCar(Car.builder()
                    .brand("Toyota").model("MarkII")
                    .engineCode("1JZ-GE").horsePower(290).year(1999)
                    .build());
            garage.addCar(Car.builder()
                    .brand("Mers").model("Mark4I")
                    .engineCode("B56").horsePower(280).year(1999)
                    .build());
            garage.addCar(Car.builder()
                    .brand("Subaru").model("MARCHOK")
                    .engineCode("2JZ-GTE").horsePower(280).year(1999)
                    .build());
            garage.addCar(Car.builder()
                    .brand("Benz").model("MAKKR")
                    .engineCode("1JZ-GTE").horsePower(280).year(1999)
                    .build());
            garage.addCar(new Car("Toyota", "MAM", "2JZ-GTE", 280, 1999));
            owners.save(new Owner("Yurii", "Samara"));
            owners.save(new Owner("Egor", "Samara"));
            owners.save(new Owner("Anton", "Samara"));

        } catch (StorageFullException e) {
            System.out.println(e.getMessage());
        }
    }

    static boolean demoDelete(Long id) {
        boolean delete = cars.deleteById(id);
        if (!delete) {
            System.out.println("Не было такой машины");
        } else {
            System.out.println("Машина с id: " + id + " Удалена");
        }
        return delete;
    }

    static void runParallelImport() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 1000; i++) {
            int n = i;
            pool.submit(() -> {
                try {
                    garage.addCar(new Car("Brand" + n, "Model" + n, "ENG", 200 + n % 100, 2000));
                } catch (StorageFullException e) {
                    System.out.println(e.getMessage());
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
    }

    static void demoOptionalStyles() {
        try {
            Car orBoom = cars.findById(999L).orElseThrow(() -> new EntityNotFoundException("Нет такой машины с id 999"));
            System.out.println("Нашли " + orBoom);
        } catch (EntityNotFoundException e) {
            System.out.println(e.getMessage());
        }
        Car defaultCar = cars.findById(1L).orElseThrow(() -> new EntityNotFoundException("Нет такой машины с id 1"));
        cars.findById(999L).ifPresent(c -> System.out.println("Нашли " + c));
        Car withDefault = cars.findById(999L).orElse(defaultCar);
        System.out.println("Вместо 999 нашли " + withDefault);
    }

    static void printAnalytics(GarageStats stats) {

        List<String> topModels = cars.findAll().stream()
                .filter(c -> c.getHorsePower() > 260)
                .map(Car::getModel)
                .sorted()
                .toList();
        Map<String, List<Car>> groupedByBrands = cars.findAll().stream()
                .collect(Collectors.groupingBy(Car::getBrand));
        Map<String, Long> allBrandCars = cars.findAll().stream()
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
        System.out.println(stats.averageHp() + " - Средняя мощность машин");
        System.out.println("---------------------------");
        allBrandCars.forEach((brand, n) -> System.out.println(StringUtils.capitalize(brand) + ": " + n));
        System.out.println("Мощнейшая машина: " + stats.strongestModel());
        groupedByBrands.forEach((brand, c) -> System.out.println(brand + ": " + c));
        System.out.println("Все модели: " + allModels);
        System.out.println("Есть ли 2JZ-GTE - " + anyEngine);
        List<Car> carsAbove = cars.findAll().stream()
                .filter(c -> c.getHorsePower() > 270)
                .toList();
        boolean unknownEngine = cars.findAll().stream()
                .anyMatch(c -> c.getEngineCode().equals("2JZ-E"));
        System.out.println("Есть ли 2JZ-E - " + unknownEngine);
        for (Car car : carsAbove) {
            System.out.println(car);

        }

        System.out.println(garage.findBy(c -> c.getBrand().equals("Toyota")));
        System.out.println(garage.findBy(c -> c.getHorsePower() > 280));
        System.out.println(garage.findBy(c -> c.getEngineCode().equals("2JZ-GTE")));
        System.out.println(stats.count());
        System.out.println(cars.getLastId());
    }

    static void demoFuture() throws InterruptedException {
        try (ExecutorService statsPool = Executors.newFixedThreadPool(2)) {

            Future<Double> avgFuture = statsPool.submit(() -> garage.stats().averageHp());
            System.out.println("Среднее из другого потока: " + avgFuture.get());
        } catch (ExecutionException e) {
            System.out.println(e.getCause());
        }
        ```
        static void carBuilder()
        {
            System.out.println("Введите Брэнд,Модель,Двигатель,Год и Число л.с через enter ");

            String brand = scan.nextLine().trim();
            String model = scan.nextLine().trim();
            String engineCode = scan.nextLine().trim();
            int year;
            int horsePower;
            while (true)
            {
                try
                {
                    year = Integer.parseInt(scan.nextLine().trim());
                    break;
                }
                catch (NumberFormatException e)
                {
                    System.out.println("Это не число попробуй ещё раз");
                }
            }
            while (true)
            {
                try
                {
                    horsePower = Integer.parseInt(scan.nextLine().trim());
                    if(horsePower > 0)
                    {
                        break;
                    }
                    else
                    {
                        System.out.println("Число лошадиных сил не должно быть меньше или равно нулю");
                    }
                }
                catch (NumberFormatException e)
                {
                    System.out.println("Это не число попробуй ещё раз");
                }
            }
    }

    public static void main(String[] args) throws InterruptedException {

        cars = new InMemoryRepository<>(Car.class, 1000);
        owners = new InMemoryRepository<>(Owner.class, 10);
        garage = new GarageService(cars);

        seedGarage();

        System.out.println("Число машин: " + garage.stats().count());

        demoDelete(3L);

        System.out.println("Число машин: " + garage.stats().count());

        printIds(cars.findAll());
        printIds(owners.findAll());

        // закомментированный для отладки
        runParallelImport();
        GarageStats stats = garage.stats();

        demoOptionalStyles();

        printAnalytics(stats);

        demoFuture();

        counterDemo();

    }


}