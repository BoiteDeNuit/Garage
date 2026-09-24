![CI](https://github.com/BoiteDeNuit/Garage/actions/workflows/ci.yml/badge.svg)

# Garage

REST API для учёта машин. Начинался как консольное приложение на коллекциях (тег `v1.0`)
и вырос до Spring Boot с JWT, Kafka, Redis и метриками.

## Стек

Java 21, Spring Boot 4.1, Spring Security, Hibernate 7, PostgreSQL 16, Flyway, Kafka (KRaft),
Redis 7, springdoc-openapi, Micrometer + Prometheus, JUnit 5, Mockito, Testcontainers,
Docker compose, GitHub Actions.

## Запуск

Нужен Docker с памятью от 3 ГБ.

```bash
cp .env.example .env
```

В `.env` заполнить `DB_PASSWORD` и `JWT_SECRET`. Секрет не короче 32 символов:

```bash
openssl rand -base64 48
```

```bash
docker compose up -d --build
```

Первая сборка идёт внутри Docker и занимает несколько минут.

| Что | Адрес |
|---|---|
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9090 |
| kafka-ui | http://localhost:8090 |
| Postgres | localhost:5433 |

## Авторизация

Flyway создаёт пользователя `admin` с паролем `admin`. Перед деплоем пароль сменить.

```bash
curl -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

Токен из ответа передаётся в заголовке `Authorization: Bearer <токен>`. Живёт 30 минут.
Чтение открыто всем. Добавлять и удалять может только `ADMIN`.
На вход 5 попыток в минуту с одного адреса. Шестая получит 429.

## API

| Метод | Путь | Доступ | Что делает |
|---|---|---|---|
| POST | `/auth/login` | все | токен |
| GET | `/api/cars` | все | список с пагинацией. `?brand=` ищет без учёта регистра |
| GET | `/api/cars/{id}` | все | машина по id |
| POST | `/api/cars` | ADMIN | добавить машину |
| DELETE | `/api/cars/{id}` | ADMIN | удалить машину |
| GET | `/api/cars/stats` | все | количество, средняя мощность, самая мощная модель |
| GET | `/api/cars/{id}/price?currency=USD` | все | цена по курсу ЦБ |

Пагинация: `?page=0&size=20&sort=year,desc`. Больше 100 на страницу не отдаётся.

Все ошибки приходят в одном формате:

```json
{
  "timestamp": "2026-09-24T19:12:35",
  "status": 404,
  "error": "Not Found",
  "message": "Машина с id: 99 не найдена",
  "path": "/api/cars/99"
}
```

400 ошибка в запросе, 401 нет токена, 403 не хватает прав, 404 нет машины или пути,
405 и 415 не тот метод или формат, 429 лимит входа, 503 не ответил ЦБ.
500 только если ошибка у нас.

## Как устроено

- Сущности не выходят из сервиса. Наружу только DTO на `record`.
- Схему базы ведёт Flyway (V1–V6). Hibernate её только проверяет (`ddl-auto: validate`).
- Новая машина публикует событие в топик `car-created`. Его читают две группы: `notifications` и `analytics`.
- Машина по id кэшируется в Redis на 10 минут. Без Redis приложение работает дальше:
  кэш просто промахивается, а лимит входа не действует.
- Курс берётся с зеркала ЦБ `cbr-xml-daily.ru`. Таймауты 3 с на подключение и 5 с на чтение.
  Если ЦБ не ответил, клиент получит 503.
- Статистика считается агрегатами в SQL.
- Поиск по бренду идёт по индексу `UPPER(brand)`. Hibernate ищет через `upper()`, поэтому
  обычный индекс по `brand` не работал. На 100 000 строк 0,34 мс против 119 мс.
- Деньги в `BigDecimal`. Округление один раз в конце.

## Метрики

`/actuator/prometheus` собирает Prometheus раз в 15 секунд.

| Метрика | Что считает |
|---|---|
| `garage_cars_added_total` | добавленные машины |
| `http_client_requests_seconds{client_name="www.cbr-xml-daily.ru"}` | время запросов к ЦБ |
| `cache_gets_total{cache="cars"}` | попадания и промахи кэша |

## Тесты

```bash
./mvnw verify
```

Нужен запущенный Docker: часть тестов поднимает контейнеры через Testcontainers.
54 теста: unit на Mockito, контроллер через `@WebMvcTest`, клиент ЦБ на встроенном
`HttpServer`, репозитории на настоящем Postgres и полный контекст с Postgres, Kafka и Redis.
CI гоняет их на каждый push в `main` и на каждый PR.

## Postman

`postman/Garage.postman_collection.json`: импорт и Run. Коллекция проходит весь путь
от логина до удаления машины и убирает за собой.

## Структура

```
com.example
├── controller   REST без логики
├── service      бизнес-логика, транзакции, кэш, события
├── repository   Spring Data JPA
├── model        сущности
├── dto          record и маппер
├── exception    свои исключения и общий обработчик
├── security     JWT фильтр и конфиг
├── client       клиент ЦБ
├── config       Kafka, кэш, Swagger
├── event        событие для Kafka
├── listener     слушатели Kafka
└── ratelimit    лимит входа на Redis
```
