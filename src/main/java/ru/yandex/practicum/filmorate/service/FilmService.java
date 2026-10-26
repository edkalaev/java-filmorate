package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Collection;
import java.util.List;

@Service
public class FilmService {

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;

    @Autowired
    public FilmService(@Qualifier("filmDbStorage") FilmStorage filmStorage,
                       @Qualifier("userDbStorage") UserStorage userStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
    }

    public Collection<Film> findAll() {
        return filmStorage.findAll();
    }

    public Film getFilmById(long id) {
        return filmStorage.getFilmById(id);
    }

    public Film create(Film film) {
        return filmStorage.create(film);
    }

    @Transactional
    public Film update(Film film) {
        return filmStorage.update(film);
    }

    @Transactional
    public void addLike(long filmId, long userId) {
        filmStorage.getFilmById(filmId);
        userStorage.getUserById(userId);
        filmStorage.addLike(filmId, userId);
    }

    @Transactional
    public void removeLike(long filmId, long userId) {
        filmStorage.getFilmById(filmId);
        userStorage.getUserById(userId);
        filmStorage.removeLike(filmId, userId);
    }

    public List<Film> getPopularFilms(int count) {
        if (count < 0) {
            throw new ConditionsNotMetException("Количество фильмов не может быть отрицательным");
        }
        return filmStorage.getPopularFilms(count);
    }
}
