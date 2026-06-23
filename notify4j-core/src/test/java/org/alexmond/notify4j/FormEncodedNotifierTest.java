package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the form-encoded extension point (the
 * {@link AbstractHttpNotifier#contentType()} override +
 * {@link AbstractHttpNotifier#formEncode}) that lets non-JSON channels like
 * Pushover/Twilio drop in.
 */
class FormEncodedNotifierTest {

	private HttpServer server;

	private final AtomicReference<String> contentType = new AtomicReference<>();

	private final AtomicReference<String> body = new AtomicReference<>();

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", (exchange) -> {
			contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsFormEncodedBodyWithFormContentType() {
		String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/send";
		new FormNotifier<Evt>(url, Evt::id, Evt::status, Evt::message).notify(new Evt(1, "FAILED", "hello world & co"));

		assertThat(contentType.get()).isEqualTo("application/x-www-form-urlencoded");
		assertThat(body.get()).isEqualTo("token=abc&message=hello+world+%26+co");
	}

	/** A minimal form-encoded channel built on the new extension point. */
	static final class FormNotifier<E> extends AbstractHttpNotifier<E> {

		FormNotifier(String url, Function<E, Object> idFn, Function<E, String> statusFn,
				Function<E, String> messageFn) {
			super(url, idFn, statusFn, messageFn, List.of());
		}

		@Override
		protected String contentType() {
			return "application/x-www-form-urlencoded";
		}

		@Override
		protected String payload(E event) {
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("token", "abc");
			fields.put("message", this.messageFn.apply(event));
			return formEncode(fields);
		}

	}

	record Evt(long id, String status, String message) {
	}

}
