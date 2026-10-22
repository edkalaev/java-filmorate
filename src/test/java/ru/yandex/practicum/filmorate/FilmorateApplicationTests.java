package ru.yandex.practicum.filmorate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:filmorate-context-test")
class FilmorateApplicationTests {

	@Test
	void contextLoads() {
	}
}
