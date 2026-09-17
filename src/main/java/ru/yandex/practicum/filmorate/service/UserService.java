package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class UserService {

    private final UserStorage userStorage;

    @Autowired
    public UserService(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public Collection<User> getUsers() {
        return userStorage.getUsers();
    }

    public User createUser(User user) {
        return userStorage.createUser(user);
    }

    public User updateUser(User user) {
        return userStorage.updateUser(user);
    }

    public User getUserById(long id) {
        return userStorage.getUserById(id);
    }

    public void addFriend(long userId, long friendId) {
        User user = userStorage.getUserById(userId);
        User friend = userStorage.getUserById(friendId);

        user.getFriends().add(friendId);
        friend.getFriends().add(userId);

        userStorage.updateUser(user);
        userStorage.updateUser(friend);
    }

    public void removeFriend(long userId, long friendId) {
        User user = userStorage.getUserById(userId);
        User friend = userStorage.getUserById(friendId);

        user.getFriends().remove(friendId);
        friend.getFriends().remove(userId);

        userStorage.updateUser(user);
        userStorage.updateUser(friend);
    }

    public List<User> getUserFriends(long userId) {
        User user = userStorage.getUserById(userId);

        List<User> userFriends = new ArrayList<>();

        for (Long friendId : user.getFriends()) {
            User friend = userStorage.getUserById(friendId);
            userFriends.add(friend);
        }

        return userFriends;
    }

    public List<User> getCommonFriends(long userId, long otherUserId) {
        User user = userStorage.getUserById(userId);
        User otherUser = userStorage.getUserById(otherUserId);

        List<User> commonFriends = new ArrayList<>();

        for (Long friendId : user.getFriends()) {
            if (otherUser.getFriends().contains(friendId)) {
                User commonFriend = userStorage.getUserById(friendId);
                commonFriends.add(commonFriend);
            }
        }

        return commonFriends;
    }
}
