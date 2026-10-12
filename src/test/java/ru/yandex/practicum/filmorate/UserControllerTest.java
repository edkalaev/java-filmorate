package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.controller.UserController;
import ru.yandex.practicum.filmorate.exception.ConditionsNotMetException;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.service.UserService;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserControllerTest {
    private UserController userController;

    @BeforeEach
    void beforeEach() {
        userController = new UserController(
                new UserService(
                        new InMemoryUserStorage()
                )
        );
    }


    @Test
    void shouldCreateUserWithValidFields() {
        User user = makeValidUser();

        User createdUser = assertDoesNotThrow(() -> userController.createUser(user));

        assertEquals(1L, createdUser.getId());
        assertEquals("mail@mail.ru", createdUser.getEmail());
        assertEquals("login", createdUser.getLogin());
        assertEquals("User name", createdUser.getName());
        assertEquals(LocalDate.of(2000, 1, 1), createdUser.getBirthday());
    }

    @Test
    void shouldUseLoginAsNameWhenNameIsNull() {
        User user = makeValidUser();
        user.setName(null);

        User createdUser = assertDoesNotThrow(() -> userController.createUser(user));

        assertEquals("login", createdUser.getName());
    }

    @Test
    void shouldUseLoginAsNameWhenNameIsEmpty() {
        User user = makeValidUser();
        user.setName("");

        User createdUser = assertDoesNotThrow(() -> userController.createUser(user));

        assertEquals("login", createdUser.getName());
    }

    @Test
    void shouldUpdateUserWithValidFields() {
        User user = makeValidUser();
        User createdUser = userController.createUser(user);

        User updatedUser = makeValidUser();
        updatedUser.setId(createdUser.getId());
        updatedUser.setEmail("new@mail.ru");
        updatedUser.setLogin("newLogin");
        updatedUser.setName("New name");
        updatedUser.setBirthday(LocalDate.of(1999, 1, 1));

        User result = assertDoesNotThrow(() -> userController.updateUser(updatedUser));

        assertEquals(createdUser.getId(), result.getId());
        assertEquals("new@mail.ru", result.getEmail());
        assertEquals("newLogin", result.getLogin());
        assertEquals("New name", result.getName());
        assertEquals(LocalDate.of(1999, 1, 1), result.getBirthday());
    }

    @Test
    void shouldThrowExceptionWhenUpdateUserIdIsNull() {
        User user = makeValidUser();
        user.setId(null);

        assertThrows(ConditionsNotMetException.class, () -> userController.updateUser(user));
    }

    @Test
    void shouldThrowExceptionWhenUpdateUserIdIsUnknown() {
        User user = makeValidUser();
        user.setId(999L);

        assertThrows(NotFoundException.class, () -> userController.updateUser(user));
    }

    private User makeValidUser() {
        User user = new User();
        user.setEmail("mail@mail.ru");
        user.setLogin("login");
        user.setName("User name");
        user.setBirthday(LocalDate.of(2000, 1, 1));

        return user;
    }
}
