![CI](https://github.com/BoiteDeNuit/Garage/actions/workflows/ci.yml/badge.svg)

# Garage

REST API для учёта машин. Начинался как консольное приложение на коллекциях (тег `v1.0`)
и вырос до Spring Boot с JWT, Kafka, Redis и метриками.

## Стек

Java 21, Spring Boot 4.1, Spring Security, Hibernate 7, PostgreSQL 16, Flyway, Kafka (KRaft),
Redis 7, springdoc-openapi, Micrometer + Prometheus, JUnit 6, Mockito, Testcontainers,
Docker compose, GitHub Actions.

## Запуск

Нужен Docker с памятью от 3 ГБ.

```bash
cp .env.example .env
```

В `.env` заполнить `DB_PASSWORD`, `JWT_SECRET` и `ADMIN_PASSWORD`. Секрет не короче 32 символов:

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

Администратора приложение создаёт при старте из `ADMIN_USERNAME` и `ADMIN_PASSWORD`.
Поменял пароль в `.env` и перезапустил: пароль обновится. Без них приложение не стартует.

```bash
curl -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<логин>","password":"<пароль>"}'
```

Токен из ответа передаётся в заголовке `Authorization: Bearer <токен>`. Живёт 30 минут.
Читать опубликованные объявления может любой. Создавать объявления может любой вошедший,
продавцом становится владелец токена.
На вход 5 попыток в минуту с одного адреса. Шестая получит 429.

## API

| Метод | Путь | Доступ | Что делает |
|---|---|---|---|
| POST | `/auth/login` | все | токен |
| GET | `/api/listings` | все | лента опубликованных. `?brand=` ищет без учёта регистра |
| GET | `/api/listings/{id}` | все | объявление. Черновик и архив видят только продавец и ADMIN |
| POST | `/api/listings` | вошедший | черновик, 201 и `Location` |
| GET | `/api/listings/stats` | все | по опубликованным: количество, средняя мощность, самая мощная модель |
| GET | `/api/listings/{id}/price?currency=USD` | все | цена по курсу ЦБ, только для опубликованных и проданных |

Статусы объявления: `DRAFT`, `ACTIVE`, `SOLD`, `ARCHIVED`.

Пагинация: `?page=0&size=20&sort=price,asc`. Больше 100 на страницу не отдаётся.
Сортировать можно по `publishedAt`, `createdAt`, `price`, `year`, `mileageKm`, `horsePower`, `id`,
по остальному 400. По умолчанию сначала новые. К любой сортировке добавляется `id`,
чтобы при равных значениях строки не прыгали между страницами.

Все ошибки приходят в одном формате:

```json
{
  "timestamp": "2026-09-24T19:12:35",
  "status": 404,
  "error": "Not Found",
  "message": "Объявление с id: 99 не найдено",
  "path": "/api/listings/99"
}
```

400 ошибка в запросе, 401 нет токена, 403 не хватает прав, 404 нет объявления или пути,
405 и 415 не тот метод или формат, 429 лимит входа, 503 не ответил ЦБ.
500 только если ошибка у нас.

## Как устроено

- Сущности не выходят из сервиса. Наружу только DTO на `record`.
- Схему базы ведёт Flyway (V1–V8). Hibernate её только проверяет (`ddl-auto: validate`).
- V8 переименовала `cars` в `listings` с сохранением id и данных. Старые машины стали архивными
  объявлениями технического продавца `garage-legacy`, под ним нельзя войти. Цена 0 стала NULL.
  Это проверяет `MigrationOnLegacyDataTest`: накатывает V1–V7, кладёт старые данные, потом V8.
- Чужой черновик отдаёт 404, а не 403: по коду ответа не должно быть видно, что объявление есть.
- Время в объявлении ставится из `Clock`, поэтому в тестах оно фиксированное.
- Слушатели топика `listing-published` в группах `notifications` и `analytics`.
- Объявление по id кэшируется в Redis на 10 минут. В кэше карточка без учёта того, кто смотрит,
  права проверяются после кэша. Без Redis приложение работает дальше: кэш просто промахивается,
  а лимит входа не действует.
- Запись в кэш синхронная (`immediateWrites`). Spring Data Redis 4 по умолчанию пишет асинхронно:
  сразу после удаления GET иногда отдавал удалённую машину из кэша. Тесты кэша падали
  примерно в каждом четвёртом прогоне.
- Курс берётся с зеркала ЦБ `cbr-xml-daily.ru`. Таймауты 3 с на подключение и 5 с на чтение.
  Если ЦБ не ответил, клиент получит 503.
- Статистика считается агрегатами в SQL.
- Поиск по марке идёт по индексу `UPPER(brand)`. Hibernate ищет через `upper()`, поэтому
  обычный индекс по `brand` не работал. На 100 000 строк 0,34 мс против 119 мс.
- Деньги в `BigDecimal`. Округление один раз в конце.

## Метрики

`/actuator/prometheus` собирает Prometheus раз в 15 секунд.

| Метрика | Что считает |
|---|---|
| `listings_added_total` | созданные объявления. Не `listings_created`: Prometheus-клиент отрезает суффикс `_created` у счётчиков |
| `http_client_requests_seconds{client_name="www.cbr-xml-daily.ru"}` | время запросов к ЦБ |
| `cache_gets_total{cache="listings"}` | попадания и промахи кэша |

## Тесты

```bash
./mvnw verify
```

Нужен запущенный Docker: часть тестов поднимает контейнеры через Testcontainers.
81 тест: unit на Mockito, контроллер через `@WebMvcTest` с настоящей безопасностью, клиент ЦБ
на встроенном `HttpServer`, репозитории и миграции на настоящем Postgres, полный контекст
с Postgres, Kafka и Redis. Контейнеры полного контекста поднимаются один раз на весь прогон.
CI гоняет их на каждый push в `main` и на каждый PR.

## Postman

`postman/Garage.postman_collection.json` пока описывает старый API `/api/cars`.

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
