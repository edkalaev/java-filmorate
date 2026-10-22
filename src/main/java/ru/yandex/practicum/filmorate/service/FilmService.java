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
import java.util.Comparator;
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
        if (film.getId() == null) {
            throw new ConditionsNotMetException("Id должен быть указан");
        }
        Film storedFilm = filmStorage.getFilmById(film.getId());
        film.setLikes(storedFilm.getLikes());
        return filmStorage.update(film);
    }

    @Transactional
    public void addLike(long filmId, long userId) {
        Film film = filmStorage.getFilmById(filmId);

        userStorage.getUserById(userId);

        film.getLikes().add(userId);

        filmStorage.update(film);
    }

    @Transactional
    public void removeLike(long filmId, long userId) {
        Film film = filmStorage.getFilmById(filmId);

        userStorage.getUserById(userId);

        film.getLikes().remove(userId);

        filmStorage.update(film);
    }

    public List<Film> getPopularFilms(int count) {
        return filmStorage.findAll()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                (Film film) -> film.getLikes().size()
                        ).reversed()
                )
                .limit(count)
                .toList();
    }
}
