package ru.yandex.practicum.filmorate.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FriendshipStatus {
    UNCONFIRMED("Неподтверждённая"),
    CONFIRMED("Подтверждённая");

    private final String name;
}
