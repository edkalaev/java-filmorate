package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class UserService {

    private final UserStorage userStorage;

    @Autowired
    public UserService(@Qualifier("userDbStorage") UserStorage userStorage) {
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

    @Transactional
    public void addFriend(long userId, long friendId) {
        if (userId == friendId) {
            throw new ConditionsNotMetException("Нельзя добавить себя в друзья");
        }

        User user = userStorage.getUserById(userId);
        User friend = userStorage.getUserById(friendId);

        if (friend.getFriends().containsKey(userId)) {
            user.getFriends().put(friendId, FriendshipStatus.CONFIRMED);
            friend.getFriends().put(userId, FriendshipStatus.CONFIRMED);
            userStorage.updateUser(friend);
        } else {
            user.getFriends().put(friendId, FriendshipStatus.UNCONFIRMED);
        }

        userStorage.updateUser(user);
    }

    @Transactional
    public void removeFriend(long userId, long friendId) {
        User user = userStorage.getUserById(userId);
        User friend = userStorage.getUserById(friendId);

        FriendshipStatus status = user.getFriends().remove(friendId);
        if (status != null && friend.getFriends().containsKey(userId)) {
            friend.getFriends().put(userId, FriendshipStatus.UNCONFIRMED);
            userStorage.updateUser(friend);
        }

        userStorage.updateUser(user);
    }

    public List<User> getUserFriends(long userId) {
        User user = userStorage.getUserById(userId);

        List<User> userFriends = new ArrayList<>();

        for (Long friendId : user.getFriends().keySet()) {
            User friend = userStorage.getUserById(friendId);
            userFriends.add(friend);
        }

        return userFriends;
    }

    public List<User> getCommonFriends(long userId, long otherUserId) {
        User user = userStorage.getUserById(userId);
        User otherUser = userStorage.getUserById(otherUserId);

        List<User> commonFriends = new ArrayList<>();

        for (Long friendId : user.getFriends().keySet()) {
            if (otherUser.getFriends().containsKey(friendId)) {
                User commonFriend = userStorage.getUserById(friendId);
                commonFriends.add(commonFriend);
            }
        }

        return commonFriends;
    }
}
