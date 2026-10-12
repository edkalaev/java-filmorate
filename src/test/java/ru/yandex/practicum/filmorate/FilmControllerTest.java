package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.controller.FilmController;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.service.FilmService;
import ru.yandex.practicum.filmorate.storage.film.InMemoryFilmStorage;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilmControllerTest {
    private FilmController filmController;

    @BeforeEach
    void beforeEach() {
        filmController = new FilmController(
                new FilmService(
                        new InMemoryFilmStorage(),
                        new InMemoryUserStorage()
                )
        );
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

        assertEquals(createdFilm.getId(), result.getId());
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