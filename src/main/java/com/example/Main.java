    package com.example;

    import com.example.exception.StorageFullException;
    import com.example.model.Car;
    import com.example.model.Owner;
    import com.example.repository.CrudRepository;
    import com.example.repository.InMemoryRepository;
    import com.example.service.GarageService;

    import java.io.*;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Scanner;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.TimeUnit;
    import java.util.function.IntPredicate;

    public class Main {
        private static CrudRepository<Car> cars;
        private static InMemoryRepository<Owner> owners;
        private static GarageService garage;
        private static Scanner scan = new Scanner(System.in);


        static void Menu() throws InterruptedException {

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
                    case 5 -> filterHorsePowers();
                    case 6 -> runParallelImport();
                    case 7 -> deleteCarById();
                    case 0 -> running = false;
                    default -> System.out.println("Нет такого пункта");
                }
            }

        }

        static void printAll() {
            garage.returnAll();
        }
        static void exportToCSV(String path,List<Car> cars)
        {
            try (FileWriter writer = new FileWriter(path)){
                writer.write("brand,model,engineCode,horsePower,year\n");
                for(Car car : cars)
                {
                    writer.write(car.getBrand()+","
                    + car.getModel() +","
                    + car.getEngineCode() + ","
                    + car.getHorsePower() + ","
                    + car.getYear() + "\n");
                }
            }
            catch (IOException e)
            {
                System.out.println("Ошибка записи файла " + e.getMessage());
            }
        }
        static List<Car> importFromCSV(String path) {
            List<Car> cars = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    Car car = Car.builder().brand(parts[0])
                            .model(parts[1])
                            .engineCode(parts[2])
                            .horsePower(Integer.parseInt(parts[3]))
                            .year(Integer.parseInt(parts[4])).build();
                    cars.add(car);
                }
            } catch (IOException e) {
                System.out.println("Ошибка чтения Файла " + e.getMessage());
            }
            try {
                for (Car car : cars) {
                    garage.addCar(car);
                }
            } catch (StorageFullException e)
            {
                System.out.println("Гараж переполнен ");
            }
            return cars;
        }
        static void filterHorsePowers()
        {
            boolean running = true;
            while (running)
            {
                int threshold = readAnyInt("Введите порог л.c: ",v -> v >= 0,"Порог не может быть отрицательным");
                System.out.println("Введите Фильтр < или >");
                String filter = scan.nextLine().trim();
                List<Car> cars;
                switch (filter) {
                    case "<":
                        cars = garage.findBy(c -> c.getHorsePower() < threshold);
                        for(Car car : cars)
                        {
                            System.out.println(car);
                        }
                        running = false;
                        break;
                    case ">":
                         cars = garage.findBy(c -> c.getHorsePower() > threshold);
                        for(Car car : cars)
                        {
                            System.out.println(car);
                        }
                       running = false;
                        break;
                    default:
                        System.out.println("Выберите только между < и > ");
                        break;
                }
            }
        }
        static boolean deleteCarById() {
            Long id = (long) readAnyInt("Введите id любой машины: ",v -> v > 0, "Id должен быть больше нуля");
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
                int maxSize = readAnyInt("Введите число Машин для заполнения", v -> v >= 0, "Число должно быть больше или равно нулю");
                for (int i = 0; i < maxSize; i++) {
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

        static void printAnalytics() {
            System.out.println("Статистика машин в гараже");
            System.out.println("Средняя мощность машин: " + garage.stats().averageHp());
            System.out.println("Мощнейшая машина: " + garage.stats().strongestModel());
            System.out.println("Число машин в Гараже: " + garage.stats().count());
        }

        static int readAnyInt(String prompt, IntPredicate valid, String errorMsg) {
            while (true) {
                System.out.print(prompt);
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

        public static void main(String[] args) throws InterruptedException {

            cars = new InMemoryRepository<>(Car.class, 1000);
            owners = new InMemoryRepository<>(Owner.class, 10);
            garage = new GarageService(cars);
            importFromCSV("garage.csv");
            Menu();
            exportToCSV("garage.csv", garage.findBy(c -> true));

        }


    }