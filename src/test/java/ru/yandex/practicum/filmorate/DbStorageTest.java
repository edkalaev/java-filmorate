package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
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

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

        Film created = createLikedFilm(film, user.getId());
        film.setLikes(Set.of(user.getId()));
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
        first = createLikedFilm(first, user.getId());
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
    void shouldUpdateFilmFieldsAndGenresWithoutChangingLikes() {
        User firstUser = userStorage.createUser(makeUser("first"));
        User secondUser = userStorage.createUser(makeUser("second"));
        Film film = makeFilm("First");
        film.setMpa(new Mpa(1, "G"));
        film.getGenres().add(new Genre(1, "Комедия"));
        Film created = createLikedFilm(film, firstUser.getId());
        created.setName("Updated");
        created.setDescription("Updated description");
        created.setReleaseDate(LocalDate.of(2020, 1, 1));
        created.setDuration(150);
        created.setMpa(new Mpa(5, "NC-17"));
        created.setGenres(Set.of(new Genre(6, "Боевик"), new Genre(4, "Триллер")));
        created.setLikes(Set.of(secondUser.getId()));

        Film updated = filmStorage.update(created);
        assertEquals(Set.of(firstUser.getId()), updated.getLikes());
        created.setLikes(Set.of(firstUser.getId()));
        assertEquals(created, updated);
        assertEquals(created, filmStorage.getFilmById(created.getId()));
        assertEquals(2, count("film_genres"));
        assertEquals(1, count("film_likes"));

        created.setMpa(null);
        created.setReleaseDate(null);
        created.setGenres(Set.of());
        created.setLikes(Set.of());
        Film withoutGenres = filmStorage.update(created);
        assertEquals(Set.of(firstUser.getId()), withoutGenres.getLikes());
        created.setLikes(Set.of(firstUser.getId()));
        assertEquals(created, withoutGenres);
        assertEquals(0, count("film_genres"));
        assertEquals(1, count("film_likes"));
    }

    @Test
    void shouldIgnoreLikesFromFilmUpdateEvenWhenTheyContainUnknownUsers() {
        User user = userStorage.createUser(makeUser("viewer"));
        Film film = makeFilm("First");
        Film created = createLikedFilm(film, user.getId());
        created.setName("Updated");
        created.setLikes(Set.of(Long.MAX_VALUE));

        Film updated = filmStorage.update(created);

        assertEquals("Updated", updated.getName());
        assertEquals(Set.of(user.getId()), updated.getLikes());
        assertEquals(1, count("film_likes"));
        created.setLikes(null);
        assertEquals(Set.of(user.getId()), filmStorage.update(created).getLikes());
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
        Film created = createLikedFilm(film, user.getId());

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
        film = createLikedFilm(film, first.getId());

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
    void shouldLoadAllFriendsInOneQueryIncludingTheirRelationships() throws SQLException {
        User owner = userStorage.createUser(makeUser("owner"));
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        User outsider = userStorage.createUser(makeUser("outsider"));
        userService.addFriend(owner.getId(), first.getId());
        userService.addFriend(owner.getId(), second.getId());
        userService.addFriend(first.getId(), owner.getId());
        userService.addFriend(first.getId(), outsider.getId());
        userService.addFriend(second.getId(), outsider.getId());
        userService.addFriend(outsider.getId(), owner.getId());
        List<User> expected = List.of(userStorage.getUserById(first.getId()),
                userStorage.getUserById(second.getId()));

        List<User> friends = executeWithSingleQuery(service -> service.getUserFriends(owner.getId()));

        assertEquals(expected, friends);
        assertEquals(Map.of(owner.getId(), FriendshipStatus.CONFIRMED,
                outsider.getId(), FriendshipStatus.UNCONFIRMED), friends.getFirst().getFriends());
        assertEquals(Map.of(outsider.getId(), FriendshipStatus.UNCONFIRMED), friends.getLast().getFriends());
    }

    @Test
    void shouldLoadCommonFriendsInOneQueryIncludingTheirRelationships() throws SQLException {
        User owner = userStorage.createUser(makeUser("owner"));
        User otherOwner = userStorage.createUser(makeUser("other"));
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        User outsider = userStorage.createUser(makeUser("outsider"));
        userService.addFriend(owner.getId(), first.getId());
        userService.addFriend(owner.getId(), second.getId());
        userService.addFriend(owner.getId(), outsider.getId());
        userService.addFriend(otherOwner.getId(), second.getId());
        userService.addFriend(otherOwner.getId(), first.getId());
        userService.addFriend(first.getId(), owner.getId());
        userService.addFriend(first.getId(), second.getId());
        List<User> expected = List.of(userStorage.getUserById(first.getId()),
                userStorage.getUserById(second.getId()));

        List<User> friends = executeWithSingleQuery(
                service -> service.getCommonFriends(owner.getId(), otherOwner.getId()));

        assertEquals(expected, friends);
        assertEquals(2, friends.getFirst().getFriends().size());
        assertTrue(friends.getLast().getFriends().isEmpty());
        assertEquals(userService.getUserFriends(owner.getId()), executeWithSingleQuery(
                service -> service.getCommonFriends(owner.getId(), owner.getId())));
    }

    @Test
    void shouldDistinguishMissingUsersFromEmptyFriendListsInOneQuery() throws SQLException {
        User first = userStorage.createUser(makeUser("first"));
        User second = userStorage.createUser(makeUser("second"));
        assertTrue(executeWithSingleQuery(service -> service.getUserFriends(first.getId())).isEmpty());
        assertTrue(executeWithSingleQuery(
                service -> service.getCommonFriends(first.getId(), second.getId())).isEmpty());
        userService.addFriend(first.getId(), second.getId());
        assertTrue(executeWithSingleQuery(
                service -> service.getCommonFriends(first.getId(), second.getId())).isEmpty());

        assertThrows(NotFoundException.class,
                () -> executeWithSingleQuery(service -> service.getUserFriends(Long.MAX_VALUE)));
        assertThrows(NotFoundException.class, () -> executeWithSingleQuery(
                service -> service.getCommonFriends(Long.MAX_VALUE, first.getId())));
        assertThrows(NotFoundException.class, () -> executeWithSingleQuery(
                service -> service.getCommonFriends(first.getId(), Long.MAX_VALUE)));
    }

    @Test
    void shouldPersistLikesAndFindPopularFilms() {
        User user = userStorage.createUser(makeUser("first"));
        User otherUser = userStorage.createUser(makeUser("second"));
        Film unpopular = filmStorage.create(makeFilm("Unpopular"));
        Film popular = filmStorage.create(makeFilm("Popular"));
        filmService.addLike(popular.getId(), user.getId());
        filmService.addLike(popular.getId(), user.getId());

        assertEquals(1, count("film_likes"));
        assertEquals(List.of(filmStorage.getFilmById(popular.getId())), filmService.getPopularFilms(1));
        assertTrue(filmStorage.getFilmById(unpopular.getId()).getLikes().isEmpty());

        filmService.addLike(popular.getId(), otherUser.getId());
        filmService.removeLike(popular.getId(), user.getId());
        filmService.removeLike(popular.getId(), user.getId());
        assertEquals(Set.of(otherUser.getId()), filmStorage.getFilmById(popular.getId()).getLikes());
        filmService.removeLike(popular.getId(), otherUser.getId());
        assertTrue(filmStorage.getFilmById(popular.getId()).getLikes().isEmpty());
    }

    @Test
    void shouldSelectPopularFilmsWithLimitAndLoadOnlyTheirRelations() {
        User firstUser = userStorage.createUser(makeUser("first"));
        User secondUser = userStorage.createUser(makeUser("second"));
        Film zeroLikes = filmStorage.create(makeFilm("No likes"));
        Film manyGenres = makeFilm("Many genres, one like");
        manyGenres.setMpa(new Mpa(3, "PG-13"));
        manyGenres.setGenres(Set.of(new Genre(1, "Комедия"), new Genre(2, "Драма"),
                new Genre(3, "Мультфильм"), new Genre(4, "Триллер")));
        manyGenres = createLikedFilm(manyGenres, firstUser.getId());
        Film winner = makeFilm("Two likes");
        winner.setMpa(new Mpa(5, "NC-17"));
        winner.getGenres().add(new Genre(6, "Боевик"));
        winner = createLikedFilm(winner, firstUser.getId());
        filmService.addLike(winner.getId(), secondUser.getId());
        winner = filmStorage.getFilmById(winner.getId());
        Film anotherZero = filmStorage.create(makeFilm("Another film without likes"));

        assertEquals(List.of(winner), filmService.getPopularFilms(1));
        assertEquals(List.of(winner, manyGenres), filmService.getPopularFilms(2));
        assertEquals(List.of(winner, manyGenres, zeroLikes, anotherZero), filmService.getPopularFilms(10));
    }

    @Test
    void shouldOrderEqualLikeCountsByFilmIdAndRefreshOrderAfterRemovingLike() {
        User user = userStorage.createUser(makeUser("viewer"));
        Film firstZero = filmStorage.create(makeFilm("First zero"));
        Film firstLiked = filmStorage.create(makeFilm("First liked"));
        Film secondZero = filmStorage.create(makeFilm("Second zero"));
        Film secondLiked = filmStorage.create(makeFilm("Second liked"));
        filmService.addLike(secondLiked.getId(), user.getId());
        filmService.addLike(firstLiked.getId(), user.getId());

        assertEquals(List.of(firstLiked.getId(), secondLiked.getId(), firstZero.getId(), secondZero.getId()),
                filmService.getPopularFilms(10).stream().map(Film::getId).toList());

        filmService.removeLike(firstLiked.getId(), user.getId());

        assertEquals(List.of(secondLiked.getId(), firstZero.getId(), firstLiked.getId(), secondZero.getId()),
                filmService.getPopularFilms(10).stream().map(Film::getId).toList());
    }

    @Test
    void shouldHandleEmptyPopularListZeroLimitAndRejectNegativeLimit() {
        assertTrue(filmService.getPopularFilms(10).isEmpty());
        filmStorage.create(makeFilm("First"));

        assertTrue(filmService.getPopularFilms(0).isEmpty());
        assertThrows(ConditionsNotMetException.class, () -> filmService.getPopularFilms(-1));
    }

    @Test
    void shouldRejectLikesForMissingFilmOrUser() {
        User user = userStorage.createUser(makeUser("viewer"));
        Film film = filmStorage.create(makeFilm("First"));

        assertThrows(NotFoundException.class, () -> filmService.addLike(Long.MAX_VALUE, user.getId()));
        assertThrows(NotFoundException.class, () -> filmService.removeLike(Long.MAX_VALUE, user.getId()));
        assertThrows(NotFoundException.class, () -> filmService.addLike(film.getId(), Long.MAX_VALUE));
        assertThrows(NotFoundException.class, () -> filmService.removeLike(film.getId(), Long.MAX_VALUE));
        assertEquals(0, count("film_likes"));
    }

    @Test
    void shouldIgnoreInitialLikesWhenCreatingFilm() {
        User user = userStorage.createUser(makeUser("viewer"));
        Film film = makeFilm("First");
        film.getGenres().add(new Genre(1, "Комедия"));
        film.setLikes(Set.of(user.getId(), Long.MAX_VALUE));

        Film created = filmStorage.create(film);

        assertTrue(created.getLikes().isEmpty());
        assertTrue(filmStorage.getFilmById(created.getId()).getLikes().isEmpty());
        assertEquals(1, count("films"));
        assertEquals(1, count("film_genres"));
        assertEquals(0, count("film_likes"));
    }

    @Test
    void shouldRollbackFilmUpdateIncludingDeletedRelations() {
        User user = userStorage.createUser(makeUser("first"));
        Film film = makeFilm("First");
        film.getGenres().add(new Genre(1, "Комедия"));
        Film original = createLikedFilm(film, user.getId());
        Film changed = filmStorage.getFilmById(original.getId());
        changed.setName("Should be rolled back");
        changed.setGenres(Set.of(new Genre(6, "Боевик")));
        changed.setLikes(Set.of(Long.MAX_VALUE));

        jdbcTemplate.execute("ALTER TABLE film_genres ADD CONSTRAINT test_reject_updated_genre CHECK (genre_id <> 6)");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> filmStorage.update(changed));
            assertEquals(original, filmStorage.getFilmById(original.getId()));
        } finally {
            jdbcTemplate.execute("ALTER TABLE film_genres DROP CONSTRAINT test_reject_updated_genre");
        }
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

    private List<User> executeWithSingleQuery(Function<UserService, List<User>> operation) throws SQLException {
        try (Connection connection = spy(jdbcTemplate.getDataSource().getConnection())) {
            JdbcTemplate template = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            UserService service = new UserService(new UserDbStorage(template));
            try {
                return operation.apply(service);
            } finally {
                verify(connection, times(1)).prepareStatement(anyString());
                verify(connection, never()).createStatement();
            }
        }
    }

    private Film createLikedFilm(Film film, long userId) {
        Film created = filmStorage.create(film);
        filmService.addLike(created.getId(), userId);
        return filmStorage.getFilmById(created.getId());
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
