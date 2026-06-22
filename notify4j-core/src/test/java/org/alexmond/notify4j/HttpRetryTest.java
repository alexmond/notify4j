package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the transient-failure retry policy in {@link AbstractHttpNotifier}: retry on
 * 5xx/IOException, fail fast on 4xx, give up after {@code maxAttempts}. Uses a tiny
 * backoff so the tests stay fast.
 */
class HttpRetryTest {

	private HttpServer server;

	private final AtomicInteger requests = new AtomicInteger();

	private volatile int failTimes; // respond 503 for the first N requests, then 200

	private volatile int fixedCode; // if > 0, always respond with this code

	@BeforeEach
	void startServer() throws Exception {
		requests.set(0);
		failTimes = 0;
		fixedCode = 0;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/hook", (exchange) -> {
			int n = requests.incrementAndGet();
			int code;
			if (fixedCode > 0) {
				code = fixedCode;
			}
			else if (n <= failTimes) {
				code = 503;
			}
			else {
				code = 200;
			}
			exchange.sendResponseHeaders(code, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private WebhookNotifier<Evt> notifier(int maxAttempts) {
		HttpClientConfig cfg = HttpClientConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(5), maxAttempts,
				Duration.ofMillis(1));
		String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
		return new WebhookNotifier<>(url, cfg, Evt::id, Evt::status, Evt::message, List.of());
	}

	@Test
	void retriesTransientFailureThenSucceeds() {
		failTimes = 2; // 503, 503, then 200
		notifier(3).notify(new Evt(1, "FAILED", "m"));
		assertThat(requests.get()).isEqualTo(3);
	}

	@Test
	void givesUpAfterMaxAttempts() {
		fixedCode = 500; // always fails
		// failure is swallowed by AbstractEventNotifier, but only after maxAttempts tries
		assertThatCode(() -> notifier(3).notify(new Evt(2, "FAILED", "m"))).doesNotThrowAnyException();
		assertThat(requests.get()).isEqualTo(3);
	}

	@Test
	void doesNotRetryClientError() {
		fixedCode = 400; // 4xx won't succeed on retry
		notifier(3).notify(new Evt(3, "FAILED", "m"));
		assertThat(requests.get()).isEqualTo(1);
	}

	record Evt(long id, String status, String message) {
	}

}
