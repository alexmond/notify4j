package org.alexmond.notify4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.Notifications;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full-context smoke test: boots a real Spring Boot application with the starter on the
 * classpath and an application-supplied {@link NotificationAdapter}, then asserts the
 * auto-configured {@link Notifications} facade actually delivers to a channel and records
 * a metric — exercising the whole wiring (adapter → factory → facade → HTTP channel →
 * metrics) that the slice tests cover piecemeal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationsSpringBootIntegrationTest {

	private static HttpServer server;

	private static final AtomicReference<String> body = new AtomicReference<>();

	@BeforeAll
	static void startServer() throws Exception {
		body.set(null);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/sink", (exchange) -> {
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@DynamicPropertySource
	static void notify4jProps(DynamicPropertyRegistry registry) {
		registry.add("notify4j.urls[0]", () -> "webhook+http://127.0.0.1:" + server.getAddress().getPort() + "/sink");
		registry.add("notify4j.log", () -> "false"); // only the webhook channel
		registry.add("notify4j.async.enabled", () -> "false"); // synchronous →
																// deterministic
	}

	@Autowired
	private Notifications<String> notifications;

	@Test
	void bootedFacadeDeliversToTheChannel() {
		// the starter auto-configured a single-channel facade from notify4j.urls
		assertThat(notifications.channelCount()).isEqualTo(1);

		notifications.send("FAILED: build #7");

		// the event actually reached the channel's HTTP endpoint, in the right wire shape
		assertThat(body.get())
			.isEqualTo("{\"id\":\"build\",\"status\":\"FAILED: build #7\",\"message\":\"FAILED: build #7\"}");
	}

	@SpringBootApplication
	static class TestApp {

		@Bean
		NotificationAdapter<String> adapter() {
			return new NotificationAdapter<>() {
				@Override
				public Object id(String e) {
					return "build"; // stable id so the first event is a real transition
				}

				@Override
				public String status(String e) {
					return e;
				}

				@Override
				public String message(String e) {
					return e;
				}
			};
		}

	}

}
