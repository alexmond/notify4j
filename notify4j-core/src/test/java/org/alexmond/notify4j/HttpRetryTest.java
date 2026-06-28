package org.alexmond.notify4j;

import org.alexmond.notify4j.internal.WebhookNotifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

	private WebhookNotifier<Evt> asyncNotifier(int maxAttempts, NotificationMetrics metrics) {
		// nonBlockingRetry = true: delivery + backoff happen off the calling thread
		HttpClientConfig cfg = HttpClientConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(5), maxAttempts,
				Duration.ofMillis(1), true);
		String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
		WebhookNotifier<Evt> n = new WebhookNotifier<>(url, cfg, Evt::id, Evt::status, Evt::message, List.of());
		n.setMetrics(metrics);
		return n;
	}

	@Test
	void nonBlockingRetryRetriesTransientThenSucceeds() throws Exception {
		failTimes = 2; // 503, 503, then 200
		Outcomes outcomes = new Outcomes();
		asyncNotifier(3, outcomes).notify(new Evt(1, "FAILED", "m"));
		// notify() returned immediately; the retries complete off-thread
		assertThat(outcomes.sent.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(requests.get()).isEqualTo(3);
	}

	@Test
	void nonBlockingRetryGivesUpAfterMaxAttempts() throws Exception {
		fixedCode = 500; // always fails
		Outcomes outcomes = new Outcomes();
		asyncNotifier(3, outcomes).notify(new Evt(2, "FAILED", "m"));
		assertThat(outcomes.failed.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(requests.get()).isEqualTo(3);
	}

	/** Latches that fire when the async delivery records its terminal outcome. */
	static final class Outcomes implements NotificationMetrics {

		final CountDownLatch sent = new CountDownLatch(1);

		final CountDownLatch failed = new CountDownLatch(1);

		@Override
		public void recordSent(String channel) {
			sent.countDown();
		}

		@Override
		public void recordFailed(String channel) {
			failed.countDown();
		}

		@Override
		public void recordSuppressed(String channel) {
		}

	}

	record Evt(long id, String status, String message) {
	}

}
