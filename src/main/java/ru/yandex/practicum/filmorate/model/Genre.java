package ru.yandex.practicum.filmorate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Genre {
    @NotNull(message = "Id жанра должен быть указан")
    @Positive(message = "Id жанра должен быть положительным")
    private Integer id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String name;
}
