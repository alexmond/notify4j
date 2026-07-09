package org.alexmond.notify4j.cli;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the full CLI context boots — picocli-spring wiring, every {@code @Command}
 * bean, and the notify4j starter auto-config all load without a
 * {@code NotificationAdapter} present.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Notify4jCliApplicationTests {

	@Test
	void contextLoads() {
	}

}
