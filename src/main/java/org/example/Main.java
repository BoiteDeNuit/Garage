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
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

public class Main {
    private static CrudRepository<Car> cars;
    private static InMemoryRepository<Owner> owners;
    private static GarageService garage;
    private static Scanner scan = new Scanner(System.in);

    static void printIds(List<? extends Identifiable> items) {
        for (Identifiable item : items) {
            System.out.println(item.getId());
        }
    }

    static void Menu() {
        System.out.println("Меню Гаража: \n" +
                "1 - Добавить машину \n" +
                "2 - Просмотреть гараж \n" +
                "3 - Поиск по мотору \n" +
                "4 - Статистика машин в гараже \n" +
                "5 - Фильтр машин по мощности \n" +
                "6 - Случайная загрузка N машин \n" +
                "7 - Удалить машину по id \n" +
                "0 - Выход");
        boolean running = true;
        while (running) {
            int menu = readAnyInt("Введите пункт меню: ", v -> v >= 0, "Должно быть числом от 0 до 7");
            switch (menu) {
                case 1 -> readCarFromConsole();
                case 2 -> printAll();
                case 3 -> findByEngine();
                case 4 -> printAnalytics();
                case 5 -> readCarFromConsole();
                case 6 -> readCarFromConsole();
                case 7 -> readCarFromConsole();
                case 0 -> running = false;
                default -> System.out.println("Нет такого пункта");
            }
        }

    }

    static void printAll() {
        garage.returnAll();
    }

    static int runExperiment(Runnable increment, IntSupplier result) throws InterruptedException {

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int t = 0; t < 4; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < 100_000; i++) {
                        increment.run();
                    }
                });
            }
            pool.awaitTermination(30, TimeUnit.MILLISECONDS);
            return result.getAsInt();
        }
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
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
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
    }

    static void findByEngine() {
        System.out.println("Введите код двигателя");
        String engine = scan.nextLine().trim();
        System.out.println(garage.findBy(c -> c.getEngineCode().equalsIgnoreCase(engine)));
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

    static void printAnalytics() {
        System.out.println("Статистика машин в гараже");
        System.out.println("Средняя мощность машин: " + garage.stats().averageHp() );
        System.out.println("Мощнейшая машина: " + garage.stats().strongestModel());
        System.out.println("Число машин в Гараже: " + garage.stats().count());
    }

    static int readAnyInt(String prompt, IntPredicate valid, String errorMsg) {
        while (true) {
            System.out.println(prompt);
            try {
                int value = Integer.parseInt(scan.nextLine().trim());
                if (valid.test(value)) return value;
                System.out.println(errorMsg);
            } catch (NumberFormatException e) {
                System.out.println("Это не число попробуй ещё раз");
            }
        }
    }


    static void readCarFromConsole() {
        System.out.println("Введите Брэнд,Модель,Двигатель,Год и Число л.с через enter ");
        System.out.print("Бренд: ");
        String brand = scan.nextLine().trim();
        System.out.print("Модель: ");
        String model = scan.nextLine().trim();
        System.out.print("Двигатель: ");
        String engineCode = scan.nextLine().trim();
        int year = readAnyInt("Год: ", v -> v > 0, "Число должно быть больше нуля");
        int horsePower = readAnyInt("Мощность: ", v -> v > 0, "Число должно быть больше нуля");
        Car car = Car.builder().brand(brand).model(model).year(year).engineCode(engineCode).horsePower(horsePower).build();
        try {
            garage.addCar(car);
            System.out.println("Добавлена: " + car);
        } catch (StorageFullException e) {
            System.out.println("Гараж полон");
        }
    }

    static void demoFuture() throws InterruptedException {
        try (ExecutorService statsPool = Executors.newFixedThreadPool(4)) {
            Future<Double> avgFuture = statsPool.submit(() -> garage.stats().averageHp());
            System.out.println("Среднее из другого потока: " + avgFuture.get());
        } catch (ExecutionException e) {
            System.out.println(e.getCause());
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
        // runParallelImport();

        demoOptionalStyles();

        demoFuture();
        counterDemo();

        Menu();

    }


}