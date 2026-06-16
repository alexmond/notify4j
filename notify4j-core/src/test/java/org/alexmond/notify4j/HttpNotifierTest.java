package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the JDK-HttpClient channel base ({@link AbstractHttpNotifier}) against a
 * local sink.
 */
class HttpNotifierTest {

	private final List<String> bodies = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private volatile int responseCode = 200;

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/hook", (exchange) -> {
			byte[] in = exchange.getRequestBody().readAllBytes();
			bodies.add(new String(in, StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(responseCode, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
	}

	@Test
	void postsJsonPayloadOnStatusTransition() {
		var notifier = new WebhookNotifier<Evt>(url(), Evt::id, Evt::status, Evt::message, List.of("*:RUNNING"));

		notifier.notify(new Evt(7, "RUNNING", "building")); // filtered (->RUNNING)
		notifier.notify(new Evt(7, "SUCCESS", "done")); // delivered (RUNNING->SUCCESS)

		assertThat(bodies).hasSize(1);
		assertThat(bodies.get(0)).contains("\"id\":7")
			.contains("\"status\":\"SUCCESS\"")
			.contains("\"message\":\"done\"");
	}

	@Test
	void jsonStringEscapesSpecialCharacters() {
		var notifier = new WebhookNotifier<Evt>(url(), Evt::id, Evt::status, Evt::message, List.of());

		notifier.notify(new Evt(1, "FAILED", "line1\ntab\there \"quoted\""));

		assertThat(bodies).hasSize(1);
		assertThat(bodies.get(0)).contains("\\n").contains("\\t").contains("\\\"quoted\\\"");
	}

	@Test
	void serverErrorIsSwallowedNotPropagated() {
		responseCode = 500;
		var notifier = new WebhookNotifier<Evt>(url(), Evt::id, Evt::status, Evt::message, List.of());

		// AbstractEventNotifier logs and swallows — a bad channel must never break the
		// caller.
		assertThatCode(() -> notifier.notify(new Evt(2, "FAILED", "boom"))).doesNotThrowAnyException();
		assertThat(bodies).hasSize(1);
	}

	record Evt(long id, String status, String message) {
	}

}
