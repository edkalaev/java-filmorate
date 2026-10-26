package ru.yandex.practicum.filmorate.storage.film;
import ru.yandex.practicum.filmorate.model.Film;
import java.util.Collection;
import java.util.List;

public interface FilmStorage {

    Collection<Film> findAll();

    Film getFilmById(long id);

    List<Film> getPopularFilms(int count);

    Film create(Film film);

    Film update(Film film);

    void addLike(long filmId, long userId);

    void removeLike(long filmId, long userId);

    void deleteFilm(long id);
}
