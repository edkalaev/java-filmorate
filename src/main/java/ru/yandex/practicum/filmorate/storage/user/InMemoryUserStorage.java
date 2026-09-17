package ru.yandex.practicum.filmorate.storage.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.User;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class InMemoryUserStorage implements UserStorage {
    private static final Logger log = LoggerFactory.getLogger(InMemoryUserStorage.class);

    private static final long INITIAL_USER_ID = 0L;

    private final Map<Long, User> users = new HashMap<>();

    @Override
    public Collection<User> getUsers() {
        return users.values();
    }

    @Override
    public User getUserById(long id) {
        User user = users.get(id);
        if (user == null) {
            throw new NotFoundException("Пользователь с id = \" + id + \" не найден");
        }
        return user;
    }

    @Override
    public User createUser(User user) {
        setDefaultNameIfEmpty(user);

        user.setId(getUserId());
        users.put(user.getId(), user);

        log.info("Добавлен пользователь: id={}, login={}",
                user.getId(), user.getLogin());

        return user;
    }

    @Override
    public User updateUser(User newUser) {
        if (newUser.getId() == null) {
            log.warn("Ошибка обновления пользователя: id не указан");
            throw new ConditionsNotMetException("Id должен быть указан");
        }

        if (!users.containsKey(newUser.getId())) {
            log.warn(
                    "Ошибка обновления пользователя: пользователь с id={} не найден",
                    newUser.getId()
            );

            throw new NotFoundException(
                    "Пользователь с id = " + newUser.getId() + " не найден"
            );
        }

        setDefaultNameIfEmpty(newUser);

        users.put(newUser.getId(), newUser);

        log.info("Обновлён пользователь: id={}, login={}",
                newUser.getId(), newUser.getLogin());

        return newUser;
    }

    @Override
    public void deleteUser(long id) {
        users.remove(id);
    }

    private void setDefaultNameIfEmpty(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }

    private long getUserId() {
        long currentMaxId = users.keySet()
                .stream()
                .mapToLong(id -> id)
                .max()
                .orElse(INITIAL_USER_ID);

        return currentMaxId + 1;
    }
}