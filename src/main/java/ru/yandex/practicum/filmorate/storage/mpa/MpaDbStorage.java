package ru.yandex.practicum.filmorate.storage.mpa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class MpaDbStorage implements MpaStorage {
    private static final MpaRowMapper MPA_ROW_MAPPER = new MpaRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public MpaDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Mpa> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM mpa_ratings ORDER BY id", MPA_ROW_MAPPER);
    }

    @Override
    public Mpa getById(int id) {
        List<Mpa> ratings = jdbcTemplate.query("SELECT id, name FROM mpa_ratings WHERE id = ?", MPA_ROW_MAPPER, id);
        if (ratings.isEmpty()) {
            throw new NotFoundException("Рейтинг MPA с id = " + id + " не найден");
        }
        return ratings.getFirst();
    }
}
