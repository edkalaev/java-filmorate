package ru.yandex.practicum.filmorate.storage.film;

import org.springframework.jdbc.core.RowMapper;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class FilmRowMapper implements RowMapper<Film> {
    @Override
    public Film mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Film film = new Film();
        film.setId(resultSet.getLong("id"));
        film.setName(resultSet.getString("name"));
        film.setDescription(resultSet.getString("description"));
        film.setReleaseDate(resultSet.getObject("release_date", LocalDate.class));
        film.setDuration(resultSet.getInt("duration"));
        Integer mpaId = resultSet.getObject("mpa_id", Integer.class);
        if (mpaId != null) {
            film.setMpa(new Mpa(mpaId, resultSet.getString("mpa_name")));
        }
        return film;
    }
}
