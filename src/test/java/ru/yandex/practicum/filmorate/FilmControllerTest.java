package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.controller.FilmController;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilmControllerTest {
    private FilmController filmController;

    @BeforeEach
    void beforeEach() {
        filmController = new FilmController();
    }

    @Test
    void shouldCreateFilmWithValidFields() {
        Film film = makeValidFilm();

        Film createdFilm = assertDoesNotThrow(() -> filmController.createFilm(film));

        assertEquals(1L, createdFilm.getId());
        assertEquals("Film name", createdFilm.getName());
        assertEquals("Description", createdFilm.getDescription());
        assertEquals(LocalDate.of(2000, 1, 1), createdFilm.getReleaseDate());
        assertEquals(120, createdFilm.getDuration());
    }

    @Test
    void shouldThrowExceptionWhenFilmNameIsEmpty() {
        Film film = makeValidFilm();
        film.setName("");

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenFilmNameIsNull() {
        Film film = makeValidFilm();
        film.setName(null);

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldCreateFilmWhenDescriptionLengthIsExactly200() {
        Film film = makeValidFilm();
        film.setDescription("a".repeat(200));

        assertDoesNotThrow(() -> filmController.createFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenDescriptionLengthIsMoreThan200() {
        Film film = makeValidFilm();
        film.setDescription("a".repeat(201));

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldCreateFilmWhenReleaseDateIsBoundaryDate() {
        Film film = makeValidFilm();
        film.setReleaseDate(LocalDate.of(1895, 12, 28));

        assertDoesNotThrow(() -> filmController.createFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenReleaseDateIsBeforeBoundaryDate() {
        Film film = makeValidFilm();
        film.setReleaseDate(LocalDate.of(1895, 12, 27));

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldCreateFilmWhenDurationIsPositive() {
        Film film = makeValidFilm();
        film.setDuration(1);

        assertDoesNotThrow(() -> filmController.createFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenDurationIsZero() {
        Film film = makeValidFilm();
        film.setDuration(0);

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenDurationIsNegative() {
        Film film = makeValidFilm();
        film.setDuration(-1);

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenRequestBodyIsEmpty() {
        Film film = new Film();

        assertThrows(ConditionsNotMetException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldUpdateFilmWithValidFields() {
        Film film = makeValidFilm();
        Film createdFilm = filmController.createFilm(film);

        Film updatedFilm = makeValidFilm();
        updatedFilm.setId(createdFilm.getId());
        updatedFilm.setName("Updated film");
        updatedFilm.setDescription("Updated description");
        updatedFilm.setReleaseDate(LocalDate.of(2001, 1, 1));
        updatedFilm.setDuration(150);

        Film result = assertDoesNotThrow(() -> filmController.updateFilm(updatedFilm));

        assertEquals("Updated film", result.getName());
        assertEquals("Updated description", result.getDescription());
        assertEquals(LocalDate.of(2001, 1, 1), result.getReleaseDate());
        assertEquals(150, result.getDuration());
    }

    @Test
    void shouldThrowExceptionWhenUpdateFilmIdIsNull() {
        Film film = makeValidFilm();
        film.setId(null);

        assertThrows(ConditionsNotMetException.class, () -> filmController.updateFilm(film));
    }

    @Test
    void shouldThrowExceptionWhenUpdateFilmIdIsUnknown() {
        Film film = makeValidFilm();
        film.setId(999L);

        assertThrows(NotFoundException.class, () -> filmController.updateFilm(film));
    }

    private Film makeValidFilm() {
        Film film = new Film();
        film.setName("Film name");
        film.setDescription("Description");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);

        return film;
    }
}
