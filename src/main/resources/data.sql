
MERGE INTO mpa_ratings (id, name, description) KEY (id) VALUES
    (1, 'G', 'У фильма нет возрастных ограничений'),
    (2, 'PG', 'Детям рекомендуется смотреть фильм с родителями'),
    (3, 'PG-13', 'Детям до 13 лет просмотр не желателен'),
    (4, 'R', 'Лицам до 17 лет просматривать фильм можно только в присутствии взрослого'),
    (5, 'NC-17', 'Лицам до 18 лет просмотр запрещён');

MERGE INTO genres (id, code, name) KEY (id) VALUES
    (1, 'COMEDY', 'Комедия'),
    (2, 'DRAMA', 'Драма'),
    (3, 'ANIMATION', 'Мультфильм'),
    (4, 'THRILLER', 'Триллер'),
    (5, 'DOCUMENTARY', 'Документальный'),
    (6, 'ACTION', 'Боевик');

MERGE INTO friendship_statuses (id, code, name) KEY (id) VALUES
    (1, 'UNCONFIRMED', 'Неподтверждённая'),
    (2, 'CONFIRMED', 'Подтверждённая');
