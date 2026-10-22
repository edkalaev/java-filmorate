package ru.yandex.practicum.filmorate.storage.film;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.genre.GenreStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;

import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository("filmDbStorage")
@Transactional(readOnly = true)
public class FilmDbStorage implements FilmStorage {
    private static final FilmRowMapper FILM_ROW_MAPPER = new FilmRowMapper();
    private static final String SELECT_FILMS = """
            SELECT f.id, f.name, f.description, f.release_date, f.duration, f.mpa_id, m.name AS mpa_name
            FROM films AS f
            LEFT JOIN mpa_ratings AS m ON m.id = f.mpa_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final SimpleJdbcInsert filmInsert;
    private final GenreStorage genreStorage;
    private final MpaStorage mpaStorage;

    public FilmDbStorage(JdbcTemplate jdbcTemplate, GenreStorage genreStorage, MpaStorage mpaStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.genreStorage = genreStorage;
        this.mpaStorage = mpaStorage;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.filmInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("films")
                .usingGeneratedKeyColumns("id")
                .usingColumns("name", "description", "release_date", "duration", "mpa_id");
    }

    @Override
    public Collection<Film> findAll() {
        return loadRelations(jdbcTemplate.query(SELECT_FILMS + " ORDER BY f.id", FILM_ROW_MAPPER));
    }

    @Override
    public Film getFilmById(long id) {
        List<Film> films = jdbcTemplate.query(SELECT_FILMS + " WHERE f.id = ?", FILM_ROW_MAPPER, id);
        if (films.isEmpty()) {
            throw new NotFoundException("Фильм с id = " + id + " не найден");
        }
        return loadRelations(films).getFirst();
    }

    @Override
    @Transactional
    public Film create(Film film) {
        validateReferences(film);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("name", film.getName())
                .addValue("description", film.getDescription())
                .addValue("release_date", film.getReleaseDate(), Types.DATE)
                .addValue("duration", film.getDuration())
                .addValue("mpa_id", getMpaId(film.getMpa()), Types.INTEGER);
        film.setId(filmInsert.executeAndReturnKey(parameters).longValue());
        saveRelations(film);
        return getFilmById(film.getId());
    }

    @Override
    @Transactional
    public Film update(Film film) {
        if (film.getId() == null) {
            throw new ConditionsNotMetException("Id должен быть указан");
        }
        validateReferences(film);
        int updated = jdbcTemplate.update("""
                UPDATE films SET name = ?, description = ?, release_date = ?, duration = ?, mpa_id = ?
                WHERE id = ?
                """, film.getName(), film.getDescription(), film.getReleaseDate(), film.getDuration(),
                getMpaId(film.getMpa()), film.getId());
        if (updated == 0) {
            throw new NotFoundException("Фильм с id = " + film.getId() + " не найден");
        }
        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", film.getId());
        jdbcTemplate.update("DELETE FROM film_likes WHERE film_id = ?", film.getId());
        saveRelations(film);
        return getFilmById(film.getId());
    }

    @Override
    @Transactional
    public void deleteFilm(long id) {
        jdbcTemplate.update("DELETE FROM films WHERE id = ?", id);
    }

    private Integer getMpaId(Mpa mpa) {
        if (mpa == null) {
            return null;
        }
        return mpa.getId();
    }

    private void validateReferences(Film film) {
        if (film.getGenres() == null) {
            throw new ConditionsNotMetException("Список жанров не может быть null");
        }
        for (Genre genre : film.getGenres()) {
            if (genre == null || genre.getId() == null || genre.getId() <= 0) {
                throw new ConditionsNotMetException("Id жанра должен быть указан и быть положительным");
            }
            genreStorage.getById(genre.getId());
        }
        Mpa mpa = film.getMpa();
        if (mpa != null) {
            if (mpa.getId() == null || mpa.getId() <= 0) {
                throw new ConditionsNotMetException("Id рейтинга MPA должен быть указан и быть положительным");
            }
            mpaStorage.getById(mpa.getId());
        }
    }

    private List<Film> loadRelations(List<Film> films) {
        if (films.isEmpty()) {
            return films;
        }
        Map<Long, Film> filmsById = new HashMap<>();
        for (Film film : films) {
            filmsById.put(film.getId(), film);
        }
        Map<String, Object> parameters = Map.of("ids", filmsById.keySet());
        namedJdbcTemplate.query("""
                SELECT fg.film_id, g.id AS genre_id, g.name
                FROM film_genres AS fg
                JOIN genres AS g ON g.id = fg.genre_id
                WHERE fg.film_id IN (:ids)
                ORDER BY fg.film_id, g.id
                """, parameters, resultSet -> {
                    Film film = filmsById.get(resultSet.getLong("film_id"));
                    film.getGenres().add(new Genre(resultSet.getInt("genre_id"), resultSet.getString("name")));
                });
        namedJdbcTemplate.query("SELECT film_id, user_id FROM film_likes WHERE film_id IN (:ids)",
                parameters, resultSet -> {
                    Film film = filmsById.get(resultSet.getLong("film_id"));
                    film.getLikes().add(resultSet.getLong("user_id"));
                });
        return films;
    }

    private void saveRelations(Film film) {
        for (Genre genre : film.getGenres()) {
            jdbcTemplate.update("INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)",
                    film.getId(), genre.getId());
        }
        for (Long userId : film.getLikes()) {
            jdbcTemplate.update("INSERT INTO film_likes (film_id, user_id) VALUES (?, ?)", film.getId(), userId);
        }
    }
}
