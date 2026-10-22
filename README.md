# java-filmorate

Приложение для хранения фильмов и пользователей, добавления жанров и рейтингов
MPA, управления лайками и дружбой, поиска популярных фильмов и общих друзей.

[Схема в формате DBML](docs/filmorate-schema.txt) ·
[Подробное описание схемы](docs/database-design.md)

`users` и `films` содержат основные данные пользователей и фильмов. Рейтинги
MPA, жанры и статусы дружбы вынесены в справочники. У фильма может быть один
рейтинг MPA и несколько жанров; связь с жанрами хранится в `film_genres`.
Каждый лайк — отдельная строка `film_likes`.

В `friendships` хранятся направленные связи. Первый запрос создаёт строку
со статусом `UNCONFIRMED`. Встречное добавление создаёт обратную строку
и переводит обе связи в `CONFIRMED`. Подтверждение и удаление взаимной дружбы
выполняются в транзакции. Удаление убирает только исходящую связь пользователя;
обратная связь, если она есть, остаётся со статусом `UNCONFIRMED`.

Каждый столбец содержит одно значение. Составные первичные ключи таблиц связей
запрещают повторный жанр, лайк или связь дружбы. Неключевые атрибуты зависят
от ключей своих таблиц; названия справочных значений не дублируются в фильмах
или связях. Количество лайков вычисляется при запросе.

Структура H2 создаётся из [schema.sql](src/main/resources/schema.sql).
При каждом запуске таблицы удаляются вместе с данными и создаются заново
благодаря настройке `spring.sql.init.mode=always`. Сервисы приложения работают
с H2 через `UserDbStorage` и `FilmDbStorage`, выбранные с помощью `@Qualifier`.
DAO сохраняют и загружают основные поля и связи: друзей со статусами, жанры
и лайки. Запись сущности вместе со связями выполняется в одной транзакции.
После `schema.sql` выполняется [data.sql](src/main/resources/data.sql),
который заполняет справочники жанров, рейтингов MPA и статусов дружбы.
Повторное выполнение `data.sql` обновляет справочные записи без дублирования.

## Справочники и формат фильма

| Метод | Путь | Результат |
| --- | --- | --- |
| GET | `/genres` | Шесть жанров, отсортированных по ID |
| GET | `/genres/{id}` | Жанр в формате `{"id": 1, "name": "Комедия"}` |
| GET | `/mpa` | Пять рейтингов с ID от 1 до 5: G, PG, PG-13, R, NC-17 |
| GET | `/mpa/{id}` | Рейтинг в формате `{"id": 1, "name": "G"}` |

Справочники читаются через `GenreDbStorage` и `MpaDbStorage`. Для отсутствующего
ID возвращается `404 Not Found`.

При `POST /films` и `PUT /films` для жанров и MPA достаточно объектов с ID:

```json
{
  "name": "Название фильма",
  "description": "Описание",
  "releaseDate": "2000-01-01",
  "duration": 120,
  "mpa": {"id": 3},
  "genres": [{"id": 1}, {"id": 2}]
}
```

Для обновления в тело также включается `id` фильма. В ответах создания,
обновления и получения возвращаются объекты с `id` и названием из БД,
например `"mpa": {"id": 3, "name": "PG-13"}`. Повторы жанров объединяются,
жанры возвращаются по возрастанию ID. Неизвестные ссылки дают `404`,
пропущенные или неположительные ID — `400`. Поля `mpa` и `genres` остаются
необязательными; по умолчанию жанров нет. Обновление полей фильма сохраняет
поставленные лайки; они изменяются через `/films/{id}/like/{userId}`.

Дружба направленная: `PUT /users/{id}/friends/{friendId}` меняет список
отправителя. Встречный запрос подтверждает обе существующие связи.
`DELETE` по тому же пути убирает только связь отправителя. Списки друзей
и общих друзей учитывают исходящие связи обоих статусов.

## Примеры основных запросов

Именованные параметры (`:film_id`, `:user_id`, `:other_user_id`, `:count`)
передаются через `NamedParameterJdbcTemplate`. Для обычного `JdbcTemplate`
используются параметры `?`. Значение `:count` должно быть неотрицательным.

### Получение всех пользователей

```sql
SELECT id, email, login, name, birthday
FROM users
ORDER BY id;
```

### Получение всех фильмов с рейтингом MPA

```sql
SELECT f.id, f.name, f.description, f.release_date, f.duration,
       f.mpa_id, m.name AS mpa_name
FROM films AS f
LEFT JOIN mpa_ratings AS m ON m.id = f.mpa_id
ORDER BY f.id;
```

`LEFT JOIN` сохраняет в результате фильмы без указанного рейтинга.

### Получение жанров фильма

```sql
SELECT g.id, g.code, g.name
FROM film_genres AS fg
JOIN genres AS g ON g.id = fg.genre_id
WHERE fg.film_id = :film_id
ORDER BY g.id;
```

### Топ N популярных фильмов

```sql
SELECT f.id, f.name, f.description, f.release_date, f.duration, f.mpa_id,
       COALESCE(l.likes_count, 0) AS likes_count
FROM films AS f
LEFT JOIN (
    SELECT film_id, COUNT(*) AS likes_count
    FROM film_likes
    GROUP BY film_id
) AS l ON l.film_id = f.id
ORDER BY likes_count DESC, f.id ASC
LIMIT :count;
```

Фильмы без лайков тоже участвуют в выдаче. При одинаковом количестве лайков
порядок определяется ID фильма. Подсчёт выполняется отдельно от жанров,
поэтому несколько жанров не увеличивают число лайков.

### Добавление и удаление лайка

Повторный лайк не создаёт новую строку: составной ключ определяет пару
«фильм — пользователь».

```sql
MERGE INTO film_likes (film_id, user_id) KEY (film_id, user_id)
VALUES (:film_id, :user_id);

DELETE FROM film_likes
WHERE film_id = :film_id AND user_id = :user_id;
```

### Получение друзей пользователя со статусами

```sql
SELECT u.id, u.email, u.login, u.name, u.birthday,
       s.code AS friendship_status
FROM friendships AS fr
JOIN users AS u ON u.id = fr.friend_id
JOIN friendship_statuses AS s ON s.id = fr.status_id
WHERE fr.user_id = :user_id
ORDER BY u.id;
```

### Получение общих друзей

```sql
SELECT u.id, u.email, u.login, u.name, u.birthday
FROM friendships AS first_links
JOIN friendships AS second_links
  ON second_links.friend_id = first_links.friend_id
JOIN users AS u ON u.id = first_links.friend_id
WHERE first_links.user_id = :user_id
  AND second_links.user_id = :other_user_id
ORDER BY u.id;
```

Как и текущий сервис, запрос учитывает исходящие связи обоих статусов.
Для поиска только подтверждённых общих друзей добавьте условия:

```sql
AND first_links.status_id = 2
AND second_links.status_id = 2
```

Здесь `2` — ID статуса `CONFIRMED` в предложенном справочнике.
