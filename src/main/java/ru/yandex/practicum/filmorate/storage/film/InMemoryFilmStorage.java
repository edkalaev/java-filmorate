package ru.yandex.practicum.filmorate.storage.film;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class InMemoryFilmStorage implements FilmStorage {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFilmStorage.class);

    private static final long INITIAL_FILM_ID = 0L;

    private final Map<Long, Film> films = new HashMap<>();

    @Override
    public Collection<Film> findAll() {
        return films.values();
    }

    @Override
    public Film create(Film film) {
        film.setId(getFilmId());
        films.put(film.getId(), film);

        log.info("Добавлен фильм: id={}, name={}",
                film.getId(), film.getName());

        return film;
    }

    @Override
    public Film getFilmById(long id) {
        Film film = films.get(id);

        if (film == null) {
            throw new NotFoundException(
                    "Фильм с id = " + id + " не найден"
            );
        }

        return film;
    }

    @Override
    public Film update(Film film) {
        if (film.getId() == null) {
            log.warn("Ошибка обновления фильма: id не указан");

            throw new ConditionsNotMetException(
                    "Id должен быть указан"
            );
        }

        if (!films.containsKey(film.getId())) {
            log.warn(
                    "Ошибка обновления фильма: фильм с id={} не найден",
                    film.getId()
            );

            throw new NotFoundException(
                    "Фильм с id = " + film.getId() + " не найден"
            );
        }

        films.put(film.getId(), film);

        log.info("Обновлён фильм: id={}, name={}",
                film.getId(), film.getName());

        return film;
    }

    @Override
    public void deleteFilm(long id) {
        films.remove(id);

        log.info("Удалён фильм: id={}", id);
    }

    private long getFilmId() {
        long currentMaxId = films.keySet()
                .stream()
                .mapToLong(id -> id)
                .max()
                .orElse(INITIAL_FILM_ID);

        return currentMaxId + 1;
    }
}
