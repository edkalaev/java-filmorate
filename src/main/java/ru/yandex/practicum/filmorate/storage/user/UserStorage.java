package ru.yandex.practicum.filmorate.storage.user;

import ru.yandex.practicum.filmorate.model.User;

import java.util.Collection;
import java.util.List;

public interface UserStorage {

    Collection<User> getUsers();

    User getUserById(long id);

    List<User> getUserFriends(long userId);

    List<User> getCommonFriends(long userId, long otherUserId);

    User createUser(User user);

    User updateUser(User user);

    void deleteUser(long id);
}
