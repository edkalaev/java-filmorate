package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.FriendshipStatus;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.UserService;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {
    private UserService userService;
    private User firstUser;
    private User secondUser;

    @BeforeEach
    void beforeEach() {
        userService = new UserService(new InMemoryUserStorage());
        firstUser = createUser("first");
        secondUser = createUser("second");
    }

    @Test
    void shouldCreateUnconfirmedRequestWithoutAddingReverseFriendship() {
        userService.addFriend(firstUser.getId(), secondUser.getId());

        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.UNCONFIRMED),
                userService.getUserById(firstUser.getId()).getFriends());
        assertTrue(userService.getUserById(secondUser.getId()).getFriends().isEmpty());
    }

    @Test
    void shouldKeepRepeatedRequestUnconfirmed() {
        userService.addFriend(firstUser.getId(), secondUser.getId());
        userService.addFriend(firstUser.getId(), secondUser.getId());

        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.UNCONFIRMED), firstUser.getFriends());
        assertTrue(secondUser.getFriends().isEmpty());
    }

    @Test
    void shouldConfirmBothSidesWhenRecipientAddsSender() {
        userService.addFriend(firstUser.getId(), secondUser.getId());
        userService.addFriend(secondUser.getId(), firstUser.getId());

        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.CONFIRMED), firstUser.getFriends());
        assertEquals(Map.of(firstUser.getId(), FriendshipStatus.CONFIRMED), secondUser.getFriends());
    }

    @Test
    void shouldKeepRepeatedAdditionConfirmed() {
        userService.addFriend(firstUser.getId(), secondUser.getId());
        userService.addFriend(secondUser.getId(), firstUser.getId());
        userService.addFriend(firstUser.getId(), secondUser.getId());

        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.CONFIRMED), firstUser.getFriends());
        assertEquals(Map.of(firstUser.getId(), FriendshipStatus.CONFIRMED), secondUser.getFriends());
    }

    @Test
    void shouldCancelOutgoingRequest() {
        userService.addFriend(firstUser.getId(), secondUser.getId());
        userService.removeFriend(firstUser.getId(), secondUser.getId());

        assertTrue(firstUser.getFriends().isEmpty());
        assertTrue(secondUser.getFriends().isEmpty());
    }

    @Test
    void shouldRemoveOnlyOwnDirectionAndDowngradeRemainingRequest() {
        userService.addFriend(firstUser.getId(), secondUser.getId());
        userService.addFriend(secondUser.getId(), firstUser.getId());
        userService.removeFriend(secondUser.getId(), firstUser.getId());

        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.UNCONFIRMED), firstUser.getFriends());
        assertTrue(secondUser.getFriends().isEmpty());

        userService.addFriend(secondUser.getId(), firstUser.getId());

        assertEquals(Map.of(firstUser.getId(), FriendshipStatus.CONFIRMED), secondUser.getFriends());
        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.CONFIRMED), firstUser.getFriends());
    }

    @Test
    void shouldNotCancelAnotherUsersOutgoingRequestWhenOwnFriendshipIsAbsent() {
        userService.addFriend(firstUser.getId(), secondUser.getId());
        userService.removeFriend(secondUser.getId(), firstUser.getId());

        assertEquals(Map.of(secondUser.getId(), FriendshipStatus.UNCONFIRMED), firstUser.getFriends());
        assertTrue(secondUser.getFriends().isEmpty());
    }

    @Test
    void shouldFindFriendsAndCommonFriendsIncludingOutgoingRequests() {
        User commonFriend = createUser("common");
        userService.addFriend(firstUser.getId(), commonFriend.getId());
        userService.addFriend(secondUser.getId(), commonFriend.getId());
        userService.addFriend(commonFriend.getId(), firstUser.getId());

        assertEquals(List.of(commonFriend), userService.getUserFriends(firstUser.getId()));
        assertEquals(List.of(commonFriend),
                userService.getCommonFriends(firstUser.getId(), secondUser.getId()));
    }

    @Test
    void shouldRejectRequestToUnknownUserWithoutChangingFriends() {
        assertThrows(NotFoundException.class, () -> userService.addFriend(firstUser.getId(), 999L));

        assertTrue(firstUser.getFriends().isEmpty());
    }

    @Test
    void shouldRejectFriendshipWithSelf() {
        assertThrows(ConditionsNotMetException.class,
                () -> userService.addFriend(firstUser.getId(), firstUser.getId()));

        assertTrue(firstUser.getFriends().isEmpty());
    }

    private User createUser(String login) {
        User user = new User();
        user.setLogin(login);
        user.setEmail(login + "@mail.ru");
        return userService.createUser(user);
    }
}
