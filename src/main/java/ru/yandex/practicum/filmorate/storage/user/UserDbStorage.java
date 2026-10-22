package ru.yandex.practicum.filmorate.storage.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.User;

import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository("userDbStorage")
@Transactional(readOnly = true)
public class UserDbStorage implements UserStorage {
    private static final UserRowMapper USER_ROW_MAPPER = new UserRowMapper();
    private static final String SELECT_USERS = "SELECT id, email, login, name, birthday FROM users";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final SimpleJdbcInsert userInsert;

    public UserDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.userInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("users")
                .usingGeneratedKeyColumns("id")
                .usingColumns("email", "login", "name", "birthday");
    }

    @Override
    public Collection<User> getUsers() {
        return loadFriends(jdbcTemplate.query(SELECT_USERS + " ORDER BY id", USER_ROW_MAPPER));
    }

    @Override
    public User getUserById(long id) {
        List<User> users = jdbcTemplate.query(SELECT_USERS + " WHERE id = ?", USER_ROW_MAPPER, id);
        if (users.isEmpty()) {
            throw new NotFoundException("Пользователь с id = " + id + " не найден");
        }
        return loadFriends(users).getFirst();
    }

    @Override
    @Transactional
    public User createUser(User user) {
        setDefaultNameIfEmpty(user);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("email", user.getEmail())
                .addValue("login", user.getLogin())
                .addValue("name", user.getName())
                .addValue("birthday", user.getBirthday(), Types.DATE);
        user.setId(userInsert.executeAndReturnKey(parameters).longValue());
        saveFriends(user);
        return getUserById(user.getId());
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        if (user.getId() == null) {
            throw new ConditionsNotMetException("Id должен быть указан");
        }
        setDefaultNameIfEmpty(user);
        int updated = jdbcTemplate.update("""
                UPDATE users SET email = ?, login = ?, name = ?, birthday = ? WHERE id = ?
                """, user.getEmail(), user.getLogin(), user.getName(), user.getBirthday(), user.getId());
        if (updated == 0) {
            throw new NotFoundException("Пользователь с id = " + user.getId() + " не найден");
        }
        jdbcTemplate.update("DELETE FROM friendships WHERE user_id = ?", user.getId());
        saveFriends(user);
        return getUserById(user.getId());
    }

    @Override
    @Transactional
    public void deleteUser(long id) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }

    private List<User> loadFriends(List<User> users) {
        if (users.isEmpty()) {
            return users;
        }
        Map<Long, User> usersById = new HashMap<>();
        users.forEach(user -> usersById.put(user.getId(), user));
        namedJdbcTemplate.query("""
                SELECT fr.user_id, fr.friend_id, s.code
                FROM friendships AS fr
                JOIN friendship_statuses AS s ON s.id = fr.status_id
                WHERE fr.user_id IN (:ids)
                """, Map.of("ids", usersById.keySet()), resultSet -> {
                    User user = usersById.get(resultSet.getLong("user_id"));
                    user.getFriends().put(resultSet.getLong("friend_id"),
                            FriendshipStatus.valueOf(resultSet.getString("code")));
                });
        return users;
    }

    private void saveFriends(User user) {
        for (Map.Entry<Long, FriendshipStatus> friendship : user.getFriends().entrySet()) {
            Integer statusId = jdbcTemplate.queryForObject(
                    "SELECT id FROM friendship_statuses WHERE code = ?",
                    Integer.class, friendship.getValue().name());
            jdbcTemplate.update("INSERT INTO friendships (user_id, friend_id, status_id) VALUES (?, ?, ?)",
                    user.getId(), friendship.getKey(), statusId);
        }
    }

    private void setDefaultNameIfEmpty(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }
}
