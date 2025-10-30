# 🏨 Hotel Booking Platform

REST API для системы бронирования отелей, реализованная в виде микросервисного приложения на **Spring Boot 3**, **Spring Cloud**, **WebFlux** и **R2DBC** с использованием **JWT-аутентификации**, **Eureka Service Discovery** и **API Gateway**.

## 🧩 Архитектура

Проект состоит из следующих компонентов:

- **Eureka Server** — централизованный реестр сервисов.
- **API Gateway** — маршрутизация запросов, базовая проверка наличия JWT.
- **Booking Service** — управление пользователями, регистрация/авторизация, создание и управление бронированиями.
- **Hotel Service** — управление отелями и номерами, рекомендации номеров на основе статистики (`times_booked`).
- **Security Module** — общий модуль безопасности с JWT-валидацией (Resource Server).

Все сервисы используют **in-memory H2** базу данных и реактивный стек (**WebFlux + R2DBC**).

---

## 🚀 Запуск проекта

### Требования
- Java 21+
- Maven 3.8+

### Пошаговая инструкция

1. **Сборка проекта**
   ```bash
   mvn clean install
   ```
   без тестов:
   ```bash
   mvn clean install -DskipTests
   ```

2. **Запуск сервисов (в указанном порядке):**

   ```bash
   # 1. Eureka Server (порт 8761)
   mvn -pl eureka-server spring-boot:run

   # 2. Hotel Service (порт 8081)
   mvn -pl hotel-service spring-boot:run

   # 3. Booking Service (порт 8082)
   mvn -pl booking-service spring-boot:run

   # 4. API Gateway (порт 8080)
   mvn -pl api-gateway spring-boot:run
   ```

> Все сервисы автоматически регистрируются в Eureka и обнаруживают друг друга.

---

## 🔐 Аутентификация

- Используется **JWT** с ролью `USER` / `ADMIN` / `INTERNAL`.
- Срок действия access-токена — **1 час** (в учебных целях).
- Авторизация происходит через `/api/auth`.

### Пример входа:

```http
POST http://localhost:8080/api/auth
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "password"
}
```

Ответ:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "tokenType": "Bearer"
}
```

Используйте `Bearer <accessToken>` в заголовке `Authorization` для защищённых эндпойнтов.

---

## 📡 Эндпойнты через Gateway

Все запросы проходят через **API Gateway** на `http://localhost:8080`.

### Публичные эндпойнты
- `POST /api/auth` — вход

### Защищённые эндпойнты (требуется JWT)

#### Для USER:
- `GET /api/hotels` — список отелей
- `GET /api/rooms` — список всех свободных номеров
- `GET /api/rooms/recommend` — рекомендуемые номера
- `POST /api/bookings` — создать бронирование (`autoSelect: true/false`)
- `GET /api/bookings/my` — все бронирования пользователя
- `GET /api/bookings/{id}` — получить бронирование по id
- `DELETE /api/bookings/{id}` — удалить бронирование по id

#### Для ADMIN:
- `POST /api/hotels` — добавить отель
- `POST /api/rooms` — добавить номер
- `GET /api/rooms/all` — все номера (не только доступные)
- `GET /api/bookings` — все бронирования
- `GET /api/users` — список пользователей
- `POST /api/users` — создать пользователя
- `GET /api/users/{id}` — получить пользователя по id
- `GET /api/users/{id}/roles` — получить роли пользователя по id
- `PATCH /api/users/{id}` — обновить пользователя по id
- `DELETE /api/users/{id}` — удалить пользователя по id

> Внутренние эндпойнты (`/rooms/*/confirm-availability`, `/release`) защищены ролью `INTERNAL` и вызываются только между сервисами.

---

## 🧪 Предзаполненные данные

При старте сервисов автоматически создаются:

**Пользователи:**
- `user@example.com` / `password` → роль `USER`
- `admin@example.com` / `admin` → роли `USER`, `ADMIN`
- `manager@example.com` / `manager` → роли `USER`, `MANAGER`

**Отели:**
- Hotel 1 (Address 1)
- Hotel 2 (Address 2)

**Номера:**
- В каждом отеле: 101, 102, 103 (недоступен), 201, 202
- `times_booked`: 101 → 3, 102 → 2, остальные → 0

---

## 🧠 Алгоритм рекомендаций

При `autoSelect=true` система:
1. Запрашивает у Hotel Service список доступных номеров.
2. Сортирует их по возрастанию поля `times_booked` (менее загруженные — в приоритете).
3. Выбирает первый подходящий номер на указанные даты.

Занятость номера определяется через таблицу `room_blocked_dates` (блокировка по дням).

---

## 🔁 Согласованность между сервисами (Saga)

1. Booking Service создаёт бронь в статусе `PENDING`.
2. Отправляет запрос на подтверждение доступности в Hotel Service.
3. При успехе → статус `CONFIRMED`, `times_booked++`.
4. При ошибке/таймауте → вызывается компенсация (`/release`), статус → `CANCELLED`.

Используются:
- **Retry (2 попытки)** с экспоненциальной задержкой
- **Timeout = 5 сек**
- **Internal JWT** для защиты внутренних вызовов

---

## 🛠 Технологии

- Java 21
- Spring Boot 3.5.6
- Spring Cloud 2025.0.0
- Spring WebFlux + R2DBC
- H2 (in-memory)
- JWT (jjwt)
- Eureka, Spring Cloud Gateway
- Lombok

---

## 📂 Структура проекта

```
hotel-booking-platform/
├── eureka-server/
├── api-gateway/
├── hotel-service/
├── booking-service/
├── security/          # общий модуль безопасности
└── configuration/     # общий модуль конфигурации
```

## 🗃 Структура баз данных

### Booking Service (`bookingdb`)

| Таблица              | Описание                             |
|----------------------|--------------------------------------|
| `users`              | Пользователи системы                 |
| `user_roles`         | Роли пользователей                   |
| `bookings`           | Бронирования с датами и статусами    |
| `processed_requests` | Таблица запросов для идемпотентности |

**Индексы**:
- `idx_bookings_user_id` — ускоряет получение бронирований пользователя.
- `idx_bookings_room_dates` — ускоряет проверку занятости номера.

### Hotel Service (`hoteldb`)

| Таблица               | Описание                                       |
|-----------------------|------------------------------------------------|
| `hotels`              | Отели                                          |
| `rooms`               | Номера в отелях                                |
| `room_blocked_dates`  | Заблокированные даты (по одной строке на день) |

**Индексы**:
- `idx_rooms_hotel_id` — фильтрация номеров по отелю.
- `idx_rooms_times_booked` — сортировка по популярности.
- `idx_blocked_room_date` — проверка доступности на дату.
- `UNIQUE (room_id, blocked_date)` — предотвращает дублирование блокировок.

---

## 📝 Примечания

- **Swagger/OpenAPI реализован** с использованием `springdoc-openapi-starter-webflux-ui`.  
  Документация доступна через Gateway:
    - Общий UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
    - Спецификации:
        - Booking Service: `/bookings/v3/api-docs`
        - Hotel Service: `/hotels/v3/api-docs`

- **Тесты реализованы** с использованием `WebTestClient` и покрывают ключевые сценарии:
    - Аутентификация (успешная и с неверными данными)
    - Получение списка отелей и рекомендуемых номеров
    - Создание бронирования с автоподбором номера
    - Проверка защиты эндпойнтов без JWT
