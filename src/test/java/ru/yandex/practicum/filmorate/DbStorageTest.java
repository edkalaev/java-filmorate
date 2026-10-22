package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.FilmService;
import ru.yandex.practicum.filmorate.service.UserService;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:db-storage-test;DB_CLOSE_DELAY=-1")
class DbStorageTest {
    @Autowired
    @Qualifier("userDbStorage")
    private UserStorage userStorage;

    @Autowired
    @Qualifier("filmDbStorage")
    private FilmStorage filmStorage;

    @Autowired
    private UserService userService;

    @Autowired
    private FilmService filmService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM films");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldUseDatabaseStoragesInServices() {
        assertInstanceOf(UserDbStorage.class, userStorage);
        assertInstanceOf(FilmDbStorage.class, filmStorage);

        User user = userService.createUser(makeUser("first"));
        Film film = filmService.create(makeFilm("First"));

        assertEquals(user.getLogin(), jdbcTemplate.queryForObject(
                "SELECT login FROM users WHERE id = ?", String.class, user.getId()));
        assertEquals(film.getName(), jdbcTemplate.queryForObject(
                "SELECT name FROM films WHERE id = ?", String.class, film.getId()));
    }

    @Test
    void shouldSaveUserWithGeneratedIdAndLoadIndependentCopy() {
        User user = makeUser("first");
        user.setId(999L);
        User created = userStorage.createUser(user);

        User loaded = userStorage.getUserById(created.getId());

        assertTrue(created.getId() > 0);
        assertEquals(user, loaded);
        assertNotSame(created, loaded);
        loaded.setName("Changed only in memory");
        assertEquals("User first", userStorage.getUserById(created.getId()).getName());
    }

    @Test
    void shouldSaveDefaultNameAndOptionalBirthday() {
        User user = makeUser("first");
        user.setName(" ");
        user.setBirthday(null);

        User created = userStorage.createUser(user);

        assertEquals(user.getLogin(), created.getName());
        assertNull(userStorage.getUserById(created.getId()).getBirthday());
    }

    @Test
    void shouldLoadAllUsersWithTheirOwnFriendshipStatuses() {
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        User third = makeUser("third");
        third.getFriends().put(first.getId(), FriendshipStatus.CONFIRMED);
        third.getFriends().put(second.getId(), FriendshipStatus.UNCONFIRMED);
        third = userStorage.createUser(third);

        List<User> users = new ArrayList<>(userStorage.getUsers());

        assertEquals(List.of(first, second, third), users);
        assertTrue(users.getFirst().getFriends().isEmpty());
        assertEquals(Map.of(first.getId(), FriendshipStatus.CONFIRMED,
                second.getId(), FriendshipStatus.UNCONFIRMED), users.getLast().getFriends());
    }

    @Test
    void shouldUpdateUserFieldsAndReplaceOutgoingFriendships() {
        User friend = userStorage.createUser(makeUser("friend"));
        User user = makeUser("first");
        user.getFriends().put(friend.getId(), FriendshipStatus.UNCONFIRMED);
        User created = userStorage.createUser(user);
        created.setEmail("updated@mail.ru");
        created.setLogin("updated");
        created.setName(null);
        created.setBirthday(null);
        created.getFriends().clear();

        User updated = userStorage.updateUser(created);

        assertEquals("updated", updated.getName());
        assertEquals(created, userStorage.getUserById(created.getId()));
        assertTrue(updated.getFriends().isEmpty());
        assertEquals(0, count("friendships"));
    }

    @Test
    void shouldRejectMissingUserAndUpdateWithoutId() {
        assertThrows(NotFoundException.class, () -> userStorage.getUserById(Long.MAX_VALUE));
        User user = makeUser("first");
        assertThrows(ConditionsNotMetException.class, () -> userStorage.updateUser(user));
        user.setId(Long.MAX_VALUE);
        assertThrows(NotFoundException.class, () -> userStorage.updateUser(user));
    }

    @Test
    void shouldSaveFilmWithRatingGenresAndLikes() {
        User user = userStorage.createUser(makeUser("first"));
        Film film = makeFilm("First");
        film.setId(999L);
        film.setMpa(new Mpa(3, "PG-13"));
        film.getGenres().addAll(Set.of(new Genre(1, "Комедия"), new Genre(2, "Драма")));
        film.getLikes().add(user.getId());

        Film created = filmStorage.create(film);
        Film loaded = filmStorage.getFilmById(created.getId());

        assertTrue(created.getId() > 0);
        assertEquals(film, loaded);
        assertNotSame(created, loaded);
        assertEquals(2, count("film_genres"));
        assertEquals(1, count("film_likes"));
        loaded.getLikes().clear();
        assertEquals(Set.of(user.getId()), filmStorage.getFilmById(created.getId()).getLikes());
    }

    @Test
    void shouldLoadAllFilmsWithoutMixingTheirRelations() {
        User user = userStorage.createUser(makeUser("first"));
        Film first = makeFilm("First");
        first.setMpa(new Mpa(3, "PG-13"));
        first.getGenres().add(new Genre(1, "Комедия"));
        first.getLikes().add(user.getId());
        first = filmStorage.create(first);
        Film second = makeFilm("Second");
        second.setMpa(new Mpa(5, "NC-17"));
        second.getGenres().add(new Genre(6, "Боевик"));
        second = filmStorage.create(second);
        Film third = makeFilm("Third");
        third.setReleaseDate(null);
        third.setDescription(null);
        third = filmStorage.create(third);

        assertEquals(List.of(first, second, third), new ArrayList<>(filmStorage.findAll()));
        Film optionalFields = filmStorage.getFilmById(third.getId());
        assertNull(optionalFields.getMpa());
        assertNull(optionalFields.getReleaseDate());
        assertNull(optionalFields.getDescription());
        assertTrue(optionalFields.getGenres().isEmpty());
        assertTrue(optionalFields.getLikes().isEmpty());
    }

    @Test
    void shouldUpdateFilmFieldsAndReplaceGenresAndLikes() {
        User firstUser = userStorage.createUser(makeUser("first"));
        User secondUser = userStorage.createUser(makeUser("second"));
        Film film = makeFilm("First");
        film.setMpa(new Mpa(1, "G"));
        film.getGenres().add(new Genre(1, "Комедия"));
        film.getLikes().add(firstUser.getId());
        Film created = filmStorage.create(film);
        created.setName("Updated");
        created.setDescription("Updated description");
        created.setReleaseDate(LocalDate.of(2020, 1, 1));
        created.setDuration(150);
        created.setMpa(new Mpa(5, "NC-17"));
        created.setGenres(Set.of(new Genre(6, "Боевик"), new Genre(4, "Триллер")));
        created.setLikes(Set.of(secondUser.getId()));

        assertEquals(created, filmStorage.update(created));
        assertEquals(created, filmStorage.getFilmById(created.getId()));
        assertEquals(2, count("film_genres"));
        assertEquals(1, count("film_likes"));

        created.setMpa(null);
        created.setReleaseDate(null);
        created.setGenres(Set.of());
        created.setLikes(Set.of());
        assertEquals(created, filmStorage.update(created));
        assertEquals(0, count("film_genres"));
        assertEquals(0, count("film_likes"));
    }

    @Test
    void shouldRejectMissingFilmAndUpdateWithoutId() {
        assertThrows(NotFoundException.class, () -> filmStorage.getFilmById(Long.MAX_VALUE));
        Film film = makeFilm("First");
        assertThrows(ConditionsNotMetException.class, () -> filmStorage.update(film));
        film.setId(Long.MAX_VALUE);
        assertThrows(NotFoundException.class, () -> filmStorage.update(film));
    }

    @Test
    void shouldDeleteFilmWithItsGenresAndLikes() {
        User user = userStorage.createUser(makeUser("first"));
        Film film = makeFilm("First");
        film.getGenres().add(new Genre(1, "Комедия"));
        film.getLikes().add(user.getId());
        Film created = filmStorage.create(film);

        filmStorage.deleteFilm(created.getId());

        assertThrows(NotFoundException.class, () -> filmStorage.getFilmById(created.getId()));
        assertEquals(0, count("film_genres"));
        assertEquals(0, count("film_likes"));
        assertEquals(user, userStorage.getUserById(user.getId()));
    }

    @Test
    void shouldDeleteUserWithLikesAndBothFriendshipDirections() {
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        userService.addFriend(first.getId(), second.getId());
        userService.addFriend(second.getId(), first.getId());
        Film film = makeFilm("First");
        film.getLikes().add(first.getId());
        film = filmStorage.create(film);

        userStorage.deleteUser(first.getId());

        assertThrows(NotFoundException.class, () -> userStorage.getUserById(first.getId()));
        assertTrue(userStorage.getUserById(second.getId()).getFriends().isEmpty());
        assertTrue(filmStorage.getFilmById(film.getId()).getLikes().isEmpty());
        assertEquals(0, count("friendships"));
    }

    @Test
    void shouldPersistFriendshipRequestConfirmationAndRemoval() {
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        userService.addFriend(first.getId(), second.getId());
        userService.addFriend(first.getId(), second.getId());
        assertEquals(Map.of(second.getId(), FriendshipStatus.UNCONFIRMED),
                userStorage.getUserById(first.getId()).getFriends());
        assertTrue(userStorage.getUserById(second.getId()).getFriends().isEmpty());

        userService.addFriend(second.getId(), first.getId());
        userService.addFriend(first.getId(), second.getId());
        assertEquals(Map.of(second.getId(), FriendshipStatus.CONFIRMED),
                userStorage.getUserById(first.getId()).getFriends());
        assertEquals(Map.of(first.getId(), FriendshipStatus.CONFIRMED),
                userStorage.getUserById(second.getId()).getFriends());

        userService.removeFriend(first.getId(), second.getId());
        assertTrue(userStorage.getUserById(first.getId()).getFriends().isEmpty());
        assertEquals(Map.of(first.getId(), FriendshipStatus.UNCONFIRMED),
                userStorage.getUserById(second.getId()).getFriends());
    }

    @Test
    void shouldFindCommonFriendsUsingLoadedRelationships() {
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        User common = userStorage.createUser(makeUser("common"));
        userService.addFriend(first.getId(), common.getId());
        userService.addFriend(second.getId(), common.getId());
        userService.addFriend(common.getId(), first.getId());

        User loadedCommon = userStorage.getUserById(common.getId());
        assertEquals(List.of(loadedCommon), userService.getCommonFriends(first.getId(), second.getId()));
        assertEquals(List.of(loadedCommon), userService.getUserFriends(second.getId()));
    }

    @Test
    void shouldPersistLikesAndFindPopularFilms() {
        User user = userStorage.createUser(makeUser("first"));
        Film unpopular = filmStorage.create(makeFilm("Unpopular"));
        Film popular = filmStorage.create(makeFilm("Popular"));
        filmService.addLike(popular.getId(), user.getId());
        filmService.addLike(popular.getId(), user.getId());

        assertEquals(1, count("film_likes"));
        assertEquals(List.of(filmStorage.getFilmById(popular.getId())), filmService.getPopularFilms(1));
        assertTrue(filmStorage.getFilmById(unpopular.getId()).getLikes().isEmpty());

        filmService.removeLike(popular.getId(), user.getId());
        assertTrue(filmStorage.getFilmById(popular.getId()).getLikes().isEmpty());
    }

    @Test
    void shouldRollbackFilmCreationWhenLikeReferencesUnknownUser() {
        Film film = makeFilm("First");
        film.getGenres().add(new Genre(1, "Комедия"));
        film.getLikes().add(Long.MAX_VALUE);

        assertThrows(DataIntegrityViolationException.class, () -> filmStorage.create(film));

        assertTrue(filmStorage.findAll().isEmpty());
        assertEquals(0, count("film_genres"));
        assertEquals(0, count("film_likes"));
    }

    @Test
    void shouldRollbackFilmUpdateIncludingDeletedRelations() {
        User user = userStorage.createUser(makeUser("first"));
        Film film = makeFilm("First");
        film.getGenres().add(new Genre(1, "Комедия"));
        film.getLikes().add(user.getId());
        Film original = filmStorage.create(film);
        Film changed = filmStorage.getFilmById(original.getId());
        changed.setName("Should be rolled back");
        changed.setGenres(Set.of(new Genre(6, "Боевик")));
        changed.setLikes(Set.of(Long.MAX_VALUE));

        assertThrows(DataIntegrityViolationException.class, () -> filmStorage.update(changed));

        assertEquals(original, filmStorage.getFilmById(original.getId()));
    }

    @Test
    void shouldRollbackUserUpdateIncludingDeletedFriendships() {
        User friend = userStorage.createUser(makeUser("friend"));
        User user = makeUser("first");
        user.getFriends().put(friend.getId(), FriendshipStatus.UNCONFIRMED);
        User original = userStorage.createUser(user);
        User changed = userStorage.getUserById(original.getId());
        changed.setName("Should be rolled back");
        changed.setFriends(Map.of(Long.MAX_VALUE, FriendshipStatus.CONFIRMED));

        assertThrows(DataIntegrityViolationException.class, () -> userStorage.updateUser(changed));

        assertEquals(original, userStorage.getUserById(original.getId()));
    }

    @Test
    void shouldRollbackBothSidesWhenFriendshipConfirmationFails() {
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        userService.addFriend(first.getId(), second.getId());
        jdbcTemplate.execute("ALTER TABLE friendships ADD CONSTRAINT test_reject_confirmation "
                + "CHECK (user_id <> " + first.getId() + " OR status_id <> 2)");
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> userService.addFriend(second.getId(), first.getId()));

            assertEquals(Map.of(second.getId(), FriendshipStatus.UNCONFIRMED),
                    userStorage.getUserById(first.getId()).getFriends());
            assertTrue(userStorage.getUserById(second.getId()).getFriends().isEmpty());
        } finally {
            jdbcTemplate.execute("ALTER TABLE friendships DROP CONSTRAINT test_reject_confirmation");
        }
    }

    @Test
    void shouldReturnEmptyCollectionsAndAllowDeletingMissingEntities() {
        assertTrue(userStorage.getUsers().isEmpty());
        assertTrue(filmStorage.findAll().isEmpty());
        assertDoesNotThrow(() -> userStorage.deleteUser(Long.MAX_VALUE));
        assertDoesNotThrow(() -> filmStorage.deleteFilm(Long.MAX_VALUE));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private User makeUser(String login) {
        User user = new User();
        user.setEmail(login + "@mail.ru");
        user.setLogin(login);
        user.setName("User " + login);
        user.setBirthday(LocalDate.of(2000, 1, 1));
        return user;
    }

    private Film makeFilm(String name) {
        Film film = new Film();
        film.setName(name);
        film.setDescription("Description " + name);
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        return film;
    }
}
