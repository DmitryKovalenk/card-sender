# Card Sender

Тестовий застосунок для створення заявок на банківські картки, генерації їхніх номерів
та оформлення відправлень через API Нової Пошти.

Застосунок працює локально через Maven або IntelliJ IDEA. Docker Compose запускає Oracle;
Dockerfile для Java-застосунку не потрібний.

## Вимоги

- JDK 17 або новіший; `JAVA_HOME` має вказувати на встановлений JDK.
- Docker із Docker Compose v2, наприклад Docker Desktop. Або будь що, аби ви тільки змогли заранити compose-сервіс `oracle`.
- Вільні порти `1521` для Oracle та `8080` для API.
- Доступ до інтернету для першого завантаження Docker-образу та Maven-залежностей.

Maven окремо встановлювати не потрібно: у репозиторії є Maven Wrapper.
Команди нижче призначені для Bash/Zsh на macOS або Linux та виконуються з кореня проєкту.
У Windows для Maven використовуйте `mvnw.cmd`; синтаксис змінних середовища залежить від оболонки.

Перевірка інструментів:

```sh
java -version
./mvnw -version
docker compose version
```

## Повний локальний запуск

### 1. Запустити Oracle

Спочатку запустіть Docker Desktop або Docker Engine, потім виконайте:

```sh
docker compose up -d oracle
docker compose ps
```

Перший запуск Oracle може тривати кілька хвилин. Перед запуском Java дочекайтеся статусу
`healthy`; повторюйте перевірку, доки контейнер не буде готовий:

```sh
docker inspect --format '{{.State.Health.Status}}' card-sender-oracle
```

Якщо статус довго залишається `starting` або став `unhealthy`, перевірте журнал:

```sh
docker compose logs --tail=100 oracle
docker compose logs -f oracle
```

`Ctrl+C` завершує перегляд журналу, але не зупиняє контейнер.

Параметри локальної БД із `docker-compose.yml`:

| Параметр | Значення |
| --- | --- |
| Контейнер | `card-sender-oracle` |
| JDBC URL | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` |
| Service name | `FREEPDB1` |
| Користувач застосунку | `card_sender` |
| Пароль застосунку | `card_sender` |

Це локальні облікові дані для тестового завдання. Дані Oracle зберігаються в Docker volume.

### 2. Запустити застосунок із профілем `local`

```sh
NOVA_POSHTA_ENABLED=false ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Під час запуску Flyway застосує міграції, а Hibernate перевірить відповідність Java-моделей
схемі БД. Дочекайтеся повідомлення `Started CardSenderApplication`.

**У поточному `application.properties` значення `nova-poshta.enabled` за замовчуванням — `true`.**
Команда вище явно вимикає звернення до Нової Пошти, щоб локальні та демонстраційні заявки
лише накопичувалися в БД. Профіль `local` сам по собі не вимикає відправлення.

### 3. Відкрити API

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

У Swagger виберіть endpoint → **Try it out** → заповніть параметри → **Execute**.

## Інші способи запуску та профілі

### Без профілю `local`

```sh
NOVA_POSHTA_ENABLED=false ./mvnw spring-boot:run
```

Застосунок працює з тією самою БД, але фільтр журналювання HTTP-запитів і відповідей не активний.

### Через змінні середовища

```sh
export SPRING_PROFILES_ACTIVE=local
export NOVA_POSHTA_ENABLED=false
./mvnw spring-boot:run
```

Змінні діють у поточному терміналі. Альтернативні способи запуску не потрібно виконувати одночасно.

### Через IntelliJ IDEA

1. Відкрийте проєкт як Maven-проєкт і виберіть JDK 17+.
2. Запустіть Oracle командою з попереднього розділу.
3. Створіть конфігурацію запуску для `CardSenderApplication`.
4. Укажіть **Active profiles**: `local` або змінну середовища `SPRING_PROFILES_ACTIVE=local`.
5. Додайте змінну середовища `NOVA_POSHTA_ENABLED=false`.
6. Запустіть конфігурацію.

### Через зібраний JAR

```sh
./mvnw clean package
NOVA_POSHTA_ENABLED=false java -jar target/card-sender-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

`package` запускає тести. Без `ORACLE_TEST_URL` інтеграційні тести з БД будуть пропущені.

### Інша БД або порт застосунку

```sh
export DATABASE_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
export DATABASE_USERNAME='card_sender'
export DATABASE_PASSWORD='card_sender'
export SERVER_PORT=8081
NOVA_POSHTA_ENABLED=false ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Для порту `8081` відповідно змініть адреси Swagger та API. Застосунок працює на хості,
тому в JDBC URL використовується `localhost`, а не ім’я Compose-сервісу `oracle`.

## Сценарій роботи з API

### 1. Переглянути типи карток і створити заявку

```sh
curl -sS http://localhost:8080/api/card-types

curl -sS -X POST http://localhost:8080/api/card-orders \
  -H 'Content-Type: application/json' \
  -H 'X-Operator: demo' \
  -d '{"firstName":"Dmytro","lastName":"Kovalenko","cardType":"GOLD"}'
```

Доступні коди: `GOLD`, `SILVER`, `WOODEN`, `ELITE`. Вони та відповідні BIN визначені в
`domain.CardTypes`. Додавання рядка до БД не розширює enum: новий тип потребує зміни коду
та відповідного запису в БД.

Ім’я та прізвище власника картки мають бути записані латинськими літерами.
Відповідь `201 Created` містить `ID`, статус `PENDING` і порожній `CARD_NUMBER`.
`X-Operator` — оператор для аудиту, а не механізм автентифікації; допустима довжина — 1–20 символів.

### 2. Згенерувати номери й отримати заявку

```sh
curl -sS -X POST http://localhost:8080/api/card-orders/generate \
  -H 'X-Operator: demo'

# Замініть 48 на ID з відповіді на створення заявки.
CARD_ORDER_ID=48
curl -sS "http://localhost:8080/api/card-orders/$CARD_ORDER_ID"
```

Генерація обробляє доступні заявки зі статусом `PENDING` і повертає кількість оброблених записів.
Якщо заголовок оператора пропущено, використовується `CARD_GENERATOR`.
GET повертає поточний стан: `PENDING` без номера або `GENERATED` зі згенерованим номером.

Oracle-процедура створює 16-значний номер: BIN, випадкова частина та контрольна цифра за алгоритмом Луна.
Унікальність забезпечує обмеження БД; при колізії процедура повторює спробу до 10 разів.
`FOR UPDATE SKIP LOCKED` пропускає заявки, зайняті іншими транзакціями.
Поточна реалізація генерує всі вибрані номери в одній транзакції; ліміт пакетної генерації ще не додано.

### 3. Створити заявку на відправлення

Укажіть номер із поля `CARD_NUMBER`, зберігаючи початкові нулі:

```sh
CARD_NUMBER='REPLACE_WITH_GENERATED_NUMBER'

curl -sS -X POST http://localhost:8080/api/shipment-orders \
  -H 'Content-Type: application/json' \
  -H 'X-Operator: demo' \
  -d "{\"firstName\":\"Дмитро\",\"lastName\":\"Коваленко\",\"cardNumber\":\"$CARD_NUMBER\",\"phone\":\"380671234567\",\"city\":\"Київ\",\"warehouse\":\"1\"}"

curl -sS "http://localhost:8080/api/orders/status/$CARD_NUMBER"
```

Для однієї картки можна створити лише одну заявку на відправлення. Телефон має формат `380`
та ще 9 цифр. Відповідь `201` означає створення заявки в БД; роботу з перевізником виконує воркер.
Коли відправлення вимкнене, заявка залишається `PENDING`.

Помилки API мають формат `{"error":"опис помилки"}`. Некоректний запит повертає `400`,
відсутня заявка — `404`, повторна заявка на відправлення — `409`.

### Перевірка статусу за номером картки та номером ЕН

Номер картки залишається обов’язковим. За бажанням додайте параметр `waybill`, щоб перевірити,
чи належить указана накладна відправленню цієї картки:

```sh
# Використовуйте CARD_NUMBER із попереднього кроку та фактичний номер ЕН із відповіді API.
WAYBILL='REPLACE_WITH_WAYBILL'
curl -sS "http://localhost:8080/api/orders/status/$CARD_NUMBER?waybill=$WAYBILL"
```

Без `waybill` endpoint працює як раніше. Якщо параметр передано, він має містити від 1 до 30 цифр
і точно збігатися зі збереженим номером ЕН, включно з початковими нулями.
Порожнє або некоректне значення повертає `400`. Невідповідність, відсутність відправлення
чи ще не створена ЕН також повертає `400` з повідомленням
`Waybill does not match the shipment for this card`. Для невідомої картки та коректного формату ЕН
повертається `404`. Формат успішної відповіді не змінюється.

Це перевірка відповідності даних у нашій БД. Вона не викликає API відстеження Нової Пошти
і не повертає фактичне місцезнаходження посилки.

### Демонстраційні дані

```sh
curl -sS -X POST 'http://localhost:8080/demo/generate?count=100'
```

`count` — кількість пар «заявка на картку + заявка на відправлення», від 1 до 1000;
за замовчуванням — 100. Сервіс використовує активні підтримувані типи карток, генерує номери
тільки для нових заявок і записує оператора `DEMO`. Помилка скасовує всю демонстраційну партію.

Для цього сценарію запускайте застосунок із `NOVA_POSHTA_ENABLED=false`.
Демонстраційні відправлення потрапляють у ту саму чергу, що й інші: увімкнений воркер
оброблятиме їх також, включно із заявками, створеними під час попередніх запусків.

## Увімкнення Нової Пошти

Зупиніть попередній Java-процес і вкажіть власні параметри відправника:

```sh
export NOVA_POSHTA_API_KEY='your-api-key'
export NOVA_POSHTA_SENDER='your-sender-reference'
export NOVA_POSHTA_CITY_SENDER='your-sender-city-reference'
export NOVA_POSHTA_SENDER_WAREHOUSE_INDEX='your-sender-warehouse-index'
export NOVA_POSHTA_CONTACT_SENDER='your-contact-reference'
export NOVA_POSHTA_SENDERS_PHONE='your-phone-number'

NOVA_POSHTA_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Цей режим викликає `InternetDocument.save` і може створювати реальні накладні.
Нова Пошта отримує дані отримувача; номер банківської картки до запиту не включається.

Параметри воркера та клієнта:

| Властивість | За замовчуванням | Призначення |
| --- | --- | --- |
| `nova-poshta.poll-delay-ms` | `10000` | Пауза після завершення попереднього циклу, мс |
| `nova-poshta.batch-size` | `20` | Максимум відправлень за цикл, від 1 до 1000 |
| `nova-poshta.connect-timeout` | `5s` | Тайм-аут встановлення з’єднання |
| `nova-poshta.read-timeout` | `20s` | Тайм-аут читання відповіді |

Це ліміт **відправлень**, а не генерації номерів карток.
Наприклад, налаштування через аргументи запуску:

```sh
NOVA_POSHTA_ENABLED=true ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments='--nova-poshta.batch-size=10 --nova-poshta.poll-delay-ms=15000'
```

Воркер спочатку фіксує перехід до `PROCESSING`, потім звертається до перевізника,
а результат зберігає окремою транзакцією. Це захищає від одночасного надсилання однієї заявки
кількома воркерами.

| Статус у `/api/orders/status/{cardNumber}` | Значення |
| --- | --- |
| `CARD_GENERATED` | Номер картки створено, заявку на відправлення ще не створено |
| `PENDING` | Відправлення очікує обробки |
| `PROCESSING` | Обробка розпочалася або результат потребує ручної звірки |
| `SENT` | Перевізник підтвердив створення накладної, її номер збережено |
| `FAILED` | Перевізник явно відхилив запит |

`SENT` не означає фізичну доставку картки. Після тайм-ауту чи некоректної відповіді заявка
залишається `PROCESSING`: автоматично повторювати запит не можна, бо накладна вже могла бути створена.
Такі випадки потрібно звіряти з Новою Поштою. Автоматичного повторення `FAILED` також немає.

## Журналювання

Профіль `local` вмикає журналювання вхідних HTTP-запитів і відповідей для `/api/**` та `/demo/**`:
метод, шлях, статус, тривалість і JSON. Пара записів має спільний ідентифікатор обміну;
це не наскрізний MDC-контекст.

- Номери карток маскуються частково; API-ключі, паролі, токени та поля авторизації — повністю.
- Поля `phone` і `waybill` відображаються повністю; імена й адресні дані також видимі.
- Заголовки та параметри query string не записуються.
- Некоректний JSON, не-JSON тіла та тіла розміром від 8 КіБ не виводяться.
- Swagger-ресурси не журналюються цим фільтром.

Діагностика клієнта Нової Пошти працює окремо: містить категорію збою, HTTP-статус або тип
транспортної помилки. Список помилок від перевізника записується повністю, без маскування
та скорочення. Ідентифікатор заявки присутній у відповідних повідомленнях диспетчера;
клієнтські повідомлення не мають MDC-кореляції. Помилки перевізника не зберігаються в таблицях БД.

## Тести

### Без Oracle

```sh
./mvnw test
```

Не задавайте `ORACLE_TEST_URL` для цього режиму. Якщо змінна залишилася від попереднього запуску:

```sh
unset ORACLE_TEST_URL ORACLE_TEST_USERNAME ORACLE_TEST_PASSWORD
./mvnw test
```

Виконуються тести сервісів, HTTP-контрактів, валідації, конфігурації, журналювання та клієнта перевізника.
Oracle-тести пропускаються. Звернення до перевізника в тестах підмінені; реальні накладні не створюються.

### Повний набір із Oracle

Запустіть Oracle, як описано вище. Тести потребують **окремої порожньої схеми** та відмовляються
працювати зі схемою, яка вже містить таблиці. Не використовуйте `card_sender` для інтеграційних тестів.

Наведені команди створюють нову локальну тестову схему з назвою, що містить поточний час:

```sh
export ORACLE_TEST_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
export ORACLE_TEST_USERNAME="card_test_$(date +%Y%m%d_%H%M%S)"
export ORACLE_TEST_PASSWORD='local_test_password'

docker exec -i card-sender-oracle sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER ${ORACLE_TEST_USERNAME} IDENTIFIED BY ${ORACLE_TEST_PASSWORD};
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE TO ${ORACLE_TEST_USERNAME};
ALTER USER ${ORACLE_TEST_USERNAME} QUOTA UNLIMITED ON USERS;
GRANT EXECUTE ON SYS.DBMS_RANDOM TO ${ORACLE_TEST_USERNAME};
EXIT
SQL

./mvnw test
```

Цей набір перевіряє міграції, процедуру генерації та колізії, транзакційний rollback,
збереження отримувача, блокування і роботу паралельних воркерів. Відправлення в тестовому
Spring-контексті вимкнене незалежно від налаштувань основного застосунку.

Після тестів схема залишається для аналізу. Для наступного запуску повторіть створення схеми
з новою часовою позначкою. Після завершення очистьте змінні поточного термінала:

```sh
unset ORACLE_TEST_URL ORACLE_TEST_USERNAME ORACLE_TEST_PASSWORD
```

## Зупинка, повторний запуск і повне очищення

Java-застосунок зупиняється через `Ctrl+C` у терміналі або **Stop** в IntelliJ.

Зупинити Oracle, зберігши контейнер і дані:

```sh
docker compose stop oracle
```

Запустити його знову (перед запуском Java дочекайтеся `healthy`, як описано вище):

```sh
docker compose up -d oracle
```

Видалити контейнери й мережу, зберігши дані БД:

```sh
docker compose down
```

**Повністю видалити локальні дані Oracle, включно з тестовими схемами**, та створити БД заново:

```sh
# Спочатку зупиніть Java-застосунок.
docker compose down -v
docker compose up -d oracle
# Дочекайтеся healthy: docker inspect --format '{{.State.Health.Status}}' card-sender-oracle
NOVA_POSHTA_ENABLED=false ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Такий reset підходить для цього локального тестового завдання після редагування вже застосованих
міграцій. Не видаляйте лише історію Flyway: таблиці та початкові дані залишаться, і повторні
INSERT можуть завершитися помилкою дублювання. Для БД, історію та дані якої потрібно зберегти,
використовуйте нові версії міграцій.

## Структура проєкту

| Пакет / каталог | Призначення |
| --- | --- |
| `controllers` | HTTP endpoints |
| `advisors` | Єдиний формат помилок API |
| `domain` | Бізнес-сутності, типи карток, вхідні дані сервісів і результати операцій |
| `dto.request`, `dto.response` | HTTP-запити, валідація та відповіді |
| `service`, `service.demo` | Бізнес-операції та генерація демонстраційних даних |
| `repository` | JPA-репозиторії, блокування та виклик Oracle-процедури |
| `client`, `client.model` | Клієнт Нової Пошти та моделі її API |
| `delivery`, `config` | Налаштування, воркер і конфігурація Spring |
| `logging` | Локальне журналювання HTTP |
| `src/main/resources/db/migration` | П’ять Flyway-міграцій: таблиці, початкові типи та процедура |
| `src/test` | Модульні та інтеграційні тести |

Flyway керує схемою, а Hibernate працює з `ddl-auto=validate`.
`spring.jpa.open-in-view=false`; транзакції визначаються в сервісах та операціях репозиторіїв.
