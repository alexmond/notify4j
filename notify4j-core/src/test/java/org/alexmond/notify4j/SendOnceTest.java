package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The one-shot imperative send ({@link Notifications#sendOnce}): delivers a
 * {@link Message} to N channels in one synchronous call, always fires (no transition
 * suppression), counts per-channel outcomes, and never throws on a channel failure.
 */
class SendOnceTest {

	private HttpServer server;

	private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

	private final Map<String, String> lastBody = new ConcurrentHashMap<>();

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		for (String path : List.of("/a", "/b")) {
			hits.put(path, new AtomicInteger());
			server.createContext(path, (exchange) -> {
				hits.get(path).incrementAndGet();
				lastBody.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
			});
		}
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String url(String path) {
		return "webhook+http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	@Test
	void deliversToAllChannelsAndCountsThem() {
		SendResult r = Notifications.sendOnce(List.of(url("/a"), url("/b")),
				Message.of("Deploy failed", "prod rollout aborted at 4/7", Severity.ERROR));

		assertThat(hits.get("/a").get()).isEqualTo(1);
		assertThat(hits.get("/b").get()).isEqualTo(1);
		// the body reached the channel through the normal payload path (escaping intact)
		assertThat(lastBody.get("/a")).contains("\"message\":\"prod rollout aborted at 4/7\"")
			.contains("\"status\":\"ALERT\"");
		assertThat(r).isEqualTo(new SendResult(2, 2, 0));
		assertThat(r.anyFailed()).isFalse();
	}

	@Test
	void aRepeatedOneShotAlwaysFires() {
		// same content twice — a transition filter would suppress the 2nd; a one-shot
		// must not
		Notifications.sendOnce(List.of(url("/a")), Message.of("x"));
		Notifications.sendOnce(List.of(url("/a")), Message.of("x"));
		assertThat(hits.get("/a").get()).isEqualTo(2);
	}

	@Test
	void aFailingChannelIsCountedNotThrown() throws Exception {
		int deadPort;
		try (ServerSocket s = new ServerSocket(0)) {
			deadPort = s.getLocalPort();
		}
		SendResult[] out = new SendResult[1];
		assertThatCode(() -> out[0] = Notifications.sendOnce(List.of("webhook+http://127.0.0.1:" + deadPort + "/x"),
				Message.of("boom")))
			.doesNotThrowAnyException();
		assertThat(out[0]).isEqualTo(new SendResult(1, 0, 1));
		assertThat(out[0].anyFailed()).isTrue();
	}

	@Test
	void tunedHttpConfigIsCoercedToBlockingAndStillDelivers() {
		// a config built for async use (non-blocking retry) must not make sendOnce return
		// before the POST completes
		HttpClientConfig async = HttpClientConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(5), 2,
				Duration.ofMillis(10), true);
		SendResult r = Notifications.sendOnce(List.of(url("/a")), Message.of("t", "b"), async);
		assertThat(hits.get("/a").get()).isEqualTo(1);
		assertThat(r).isEqualTo(new SendResult(1, 1, 0));
	}

	@Test
	void messageFactoriesAndDefaults() {
		Message body = Message.of("just a body");
		assertThat(body.title()).isNull();
		assertThat(body.body()).isEqualTo("just a body");
		assertThat(body.severity()).isEqualTo(Severity.DEFAULT);

		assertThat(Message.of("Title", "Body").title()).isEqualTo("Title");
		assertThat(Message.of("T", "B", Severity.CRITICAL).severity()).isEqualTo(Severity.CRITICAL);
		// a null severity in the canonical constructor coalesces to DEFAULT
		assertThat(new Message("t", "b", null).severity()).isEqualTo(Severity.DEFAULT);
		// body is required
		assertThatThrownBy(() -> Message.of(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void messageAdapterMapsFieldsWithNullIdSoItAlwaysFires() {
		Notifications.MessageAdapter a = Notifications.MessageAdapter.INSTANCE;
		Message m = Message.of("Subject", "Body text", Severity.WARNING);
		assertThat(a.id(m)).isNull(); // null id => never suppressed by a transition
										// filter
		assertThat(a.status(m)).isEqualTo("ALERT");
		assertThat(a.message(m)).isEqualTo("Body text");
		assertThat(a.title(m)).isEqualTo("Subject");
		assertThat(a.severity(m)).isEqualTo(Severity.WARNING);
	}

}
