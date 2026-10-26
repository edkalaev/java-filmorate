package ru.yandex.practicum.filmorate.storage.genre;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Genre;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class GenreDbStorage implements GenreStorage {
    private static final GenreRowMapper GENRE_ROW_MAPPER = new GenreRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public GenreDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Genre> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM genres ORDER BY id", GENRE_ROW_MAPPER);
    }

    @Override
    public Genre getById(int id) {
        List<Genre> genres = jdbcTemplate.query("SELECT id, name FROM genres WHERE id = ?", GENRE_ROW_MAPPER, id);
        if (genres.isEmpty()) {
            throw new NotFoundException("Жанр с id = " + id + " не найден");
        }
        return genres.getFirst();
    }
}
