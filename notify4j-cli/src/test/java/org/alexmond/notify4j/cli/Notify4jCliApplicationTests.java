package org.alexmond.notify4j.cli;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the full CLI context boots — picocli-spring wiring and every
 * {@code @Command} bean load on the Spring-free notify4j core alone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Notify4jCliApplicationTests {

	@Test
	void contextLoads() {
	}

}
