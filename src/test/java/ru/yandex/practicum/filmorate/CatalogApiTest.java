package ru.yandex.practicum.filmorate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalog-api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CatalogApiTest {
    private static final String FILM_REQUEST =
            "{\"name\":\"Test film\","
                    + "\"description\":\"Description\","
                    + "\"releaseDate\":\"2000-01-01\","
                    + "\"duration\":120,"
                    + "\"mpa\":{\"id\":3},"
                    + "\"genres\":[{\"id\":2},{\"id\":1},{\"id\":1}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM films");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldReturnSixGenresWithIdsAndNames() throws Exception {
        JsonNode genres = read(mockMvc.perform(get("/genres"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(objectMapper.readTree(
                "[{\"id\":1,\"name\":\"Комедия\"},{\"id\":2,\"name\":\"Драма\"},"
                        + "{\"id\":3,\"name\":\"Мультфильм\"},{\"id\":4,\"name\":\"Триллер\"},"
                        + "{\"id\":5,\"name\":\"Документальный\"},{\"id\":6,\"name\":\"Боевик\"}]"
        ), genres);
    }

    @Test
    void shouldReturnExactlyFiveMpaRatingsWithIdsAndNames() throws Exception {
        JsonNode ratings = read(mockMvc.perform(get("/mpa"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(objectMapper.readTree(
                "[{\"id\":1,\"name\":\"G\"},{\"id\":2,\"name\":\"PG\"},"
                        + "{\"id\":3,\"name\":\"PG-13\"},{\"id\":4,\"name\":\"R\"},"
                        + "{\"id\":5,\"name\":\"NC-17\"}]"
        ), ratings);;
    }

    @ParameterizedTest
    @CsvSource({"1,G", "2,PG", "3,PG-13", "4,R", "5,NC-17"})
    void shouldFindEachMpaRating(int id, String name) throws Exception {
        JsonNode rating = read(mockMvc.perform(get("/mpa/{id}", id))
                .andExpect(status().isOk()).andReturn());

        assertEquals(objectMapper.valueToTree(Map.of("id", id, "name", name)), rating);
    }

    @Test
    void shouldFindGenreById() throws Exception {
        mockMvc.perform(get("/genres/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Комедия"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mpa/999", "/mpa/0", "/mpa/-1", "/genres/999", "/genres/0", "/genres/-1"})
    void shouldReturn404ForMissingDictionaryEntry(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mpa/invalid", "/genres/invalid"})
    void shouldReturn400ForNonNumericId(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isBadRequest());
    }

    @Test
    void shouldReadDictionaryNamesFromDatabase() throws Exception {
        jdbcTemplate.update("UPDATE genres SET name = ? WHERE id = 1", "Название из БД");
        try {
            mockMvc.perform(get("/genres/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Название из БД"));
            ObjectNode film = createFilm(FILM_REQUEST);
            assertEquals("Название из БД", film.path("genres").get(0).path("name").asText());
        } finally {
            jdbcTemplate.update("UPDATE genres SET name = ? WHERE id = 1", "Комедия");
        }
    }

    @Test
    void shouldCreateAndGetFilmUsingOnlyReferenceIds() throws Exception {
        ObjectNode created = createFilm(FILM_REQUEST);

        assertEquals(objectMapper.readTree("""
                [{"id":1,"name":"Комедия"}, {"id":2,"name":"Драма"}]
                """), created.path("genres"));
        assertEquals(objectMapper.readTree("{\"id\":3,\"name\":\"PG-13\"}"), created.path("mpa"));
        assertEquals(2, count("film_genres"));
        JsonNode loaded = read(mockMvc.perform(get("/films/{id}", created.path("id").asLong()))
                .andExpect(status().isOk()).andReturn());
        assertEquals(created, loaded);
        mockMvc.perform(get("/films"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mpa.id").value(3))
                .andExpect(jsonPath("$[0].genres[0].id").value(1));
    }

    @Test
    void shouldUseCanonicalNamesInsteadOfNamesFromRequest() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(FILM_REQUEST);
        request.set("mpa", objectMapper.readTree("{\"id\":1,\"name\":\"Fake rating\"}"));
        request.set("genres", objectMapper.readTree("[{\"id\":1,\"name\":\"Fake genre\"}]"));

        ObjectNode film = createFilm(request.toString());

        assertEquals("G", film.path("mpa").path("name").asText());
        assertEquals("Комедия", film.path("genres").get(0).path("name").asText());
    }

    @Test
    void shouldUpdateReferencesAndRemoveGenresWithoutDuplicates() throws Exception {
        ObjectNode created = createFilm(FILM_REQUEST);
        ObjectNode update = created.deepCopy();
        update.put("name", "Updated film");
        update.set("mpa", objectMapper.readTree("{\"id\":5}"));
        update.set("genres", objectMapper.readTree("[{\"id\":6},{\"id\":6}]"));

        JsonNode updated = read(mockMvc.perform(put("/films")
                        .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk()).andReturn());

        assertEquals("Updated film", updated.path("name").asText());
        assertEquals(objectMapper.readTree("{\"id\":5,\"name\":\"NC-17\"}"), updated.path("mpa"));
        assertEquals(objectMapper.readTree("[{\"id\":6,\"name\":\"Боевик\"}]"), updated.path("genres"));
        assertEquals(1, count("film_genres"));

        update.set("genres", objectMapper.createArrayNode());
        mockMvc.perform(put("/films").contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.genres", hasSize(0)));
        assertEquals(0, count("film_genres"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"mpa\":{\"id\":999}}", "{\"genres\":[{\"id\":999}]}"})
    void shouldRejectUnknownReferencesWithoutSavingFilm(String overrides) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(FILM_REQUEST);
        request.setAll((ObjectNode) objectMapper.readTree(overrides));

        mockMvc.perform(post("/films").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isNotFound());

        assertEquals(0, count("films"));
        assertEquals(0, count("film_genres"));
    }

    @Test
    void shouldLeaveFilmUnchangedWhenUpdateReferencesUnknownGenre() throws Exception {
        ObjectNode original = createFilm(FILM_REQUEST);
        ObjectNode update = original.deepCopy();
        update.put("name", "Must not be saved");
        update.set("genres", objectMapper.readTree("[{\"id\":999}]"));

        mockMvc.perform(put("/films").contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isNotFound());

        assertEquals(original, read(mockMvc.perform(get("/films/{id}", original.path("id").asLong()))
                .andExpect(status().isOk()).andReturn()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"mpa\":{}}", "{\"mpa\":{\"id\":0}}", "{\"mpa\":{\"id\":-1}}",
            "{\"genres\":[{}]}", "{\"genres\":[{\"id\":0}]}", "{\"genres\":[{\"id\":-1}]}",
            "{\"genres\":null}", "{\"genres\":[null]}", "{\"mpa\":\"G\"}"
    })
    void shouldValidateReferenceIds(String overrides) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(FILM_REQUEST);
        request.setAll((ObjectNode) objectMapper.readTree(overrides));

        mockMvc.perform(post("/films").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest());

        assertEquals(0, count("films"));
    }

    @Test
    void shouldAllowFilmWithoutOptionalReferences() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(FILM_REQUEST);
        request.remove("mpa");
        request.remove("genres");

        ObjectNode film = createFilm(request.toString());

        assertEquals(objectMapper.nullNode(), film.path("mpa"));
        assertEquals(objectMapper.createArrayNode(), film.path("genres"));
    }

    @Test
    void shouldKeepLikesWhenUpdatingFilmAndAllowRemovingThem() throws Exception {
        long userId = createUser("viewer");
        ObjectNode film = createFilm(FILM_REQUEST);
        long filmId = film.path("id").asLong();
        mockMvc.perform(put("/films/{id}/like/{userId}", filmId, userId)).andExpect(status().isOk());
        mockMvc.perform(put("/films/{id}/like/{userId}", filmId, userId)).andExpect(status().isOk());
        assertEquals(1, count("film_likes"));
        mockMvc.perform(get("/films/popular").param("count", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(filmId));

        ObjectNode update = (ObjectNode) objectMapper.readTree(FILM_REQUEST);
        update.put("id", filmId);
        update.put("name", "Updated after liking");
        mockMvc.perform(put("/films").contentType(MediaType.APPLICATION_JSON).content(update.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likes", hasSize(1)));
        mockMvc.perform(delete("/films/{id}/like/{userId}", filmId, userId)).andExpect(status().isOk());
        mockMvc.perform(get("/films/{id}", filmId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likes", hasSize(0)));
    }

    @Test
    void shouldKeepFriendshipDirectedAndConfirmOnlyAfterReciprocalRequest() throws Exception {
        long firstId = createUser("first");
        long secondId = createUser("second");
        mockMvc.perform(put("/users/{id}/friends/{friendId}", firstId, secondId)).andExpect(status().isOk());
        mockMvc.perform(put("/users/{id}/friends/{friendId}", firstId, secondId)).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}/friends", firstId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/users/{id}/friends", secondId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertEquals("UNCONFIRMED", friendshipStatus(firstId, secondId));

        mockMvc.perform(put("/users/{id}/friends/{friendId}", secondId, firstId)).andExpect(status().isOk());
        assertEquals("CONFIRMED", friendshipStatus(firstId, secondId));
        assertEquals("CONFIRMED", friendshipStatus(secondId, firstId));

        mockMvc.perform(delete("/users/{id}/friends/{friendId}", firstId, secondId)).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}/friends", firstId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/users/{id}/friends", secondId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        assertEquals("UNCONFIRMED", friendshipStatus(secondId, firstId));
    }

    @Test
    void shouldFindCommonOutgoingFriendsWithoutAddingReverseLinks() throws Exception {
        long firstId = createUser("first");
        long secondId = createUser("second");
        long commonId = createUser("common");
        mockMvc.perform(put("/users/{id}/friends/{friendId}", firstId, commonId)).andExpect(status().isOk());
        mockMvc.perform(put("/users/{id}/friends/{friendId}", secondId, commonId)).andExpect(status().isOk());

        mockMvc.perform(get("/users/{id}/friends/common/{otherId}", firstId, secondId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(commonId));
        mockMvc.perform(get("/users/{id}/friends", commonId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    private ObjectNode createFilm(String body) throws Exception {
        return (ObjectNode) read(mockMvc.perform(post("/films")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());
    }

    private long createUser(String login) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", login + "@mail.ru", "login", login));
        return read(mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn()).path("id").asLong();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String friendshipStatus(long userId, long friendId) {
        return jdbcTemplate.queryForObject("""
                SELECT s.code FROM friendships AS f
                JOIN friendship_statuses AS s ON s.id = f.status_id
                WHERE f.user_id = ? AND f.friend_id = ?
                """, String.class, userId, friendId);
    }
}
