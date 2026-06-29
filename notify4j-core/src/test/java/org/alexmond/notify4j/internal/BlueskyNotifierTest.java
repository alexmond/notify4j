package org.alexmond.notify4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.alexmond.notify4j.HttpClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bluesky's two-step AT Protocol flow: createSession (→ JWT + DID) then createRecord (a
 * post, authorized with the JWT).
 */
class BlueskyNotifierTest {

	private HttpServer server;

	private final AtomicReference<String> sessionBody = new AtomicReference<>();

	private final AtomicReference<String> recordBody = new AtomicReference<>();

	private final AtomicReference<String> recordAuth = new AtomicReference<>();

	private final AtomicInteger recordHits = new AtomicInteger();

	private volatile int recordStatus = 200;

	@BeforeEach
	void startServer() throws Exception {
		recordStatus = 200;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/xrpc/com.atproto.server.createSession", (exchange) -> {
			sessionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] out = "{\"accessJwt\":\"jwt-123\",\"did\":\"did:plc:abc\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, out.length);
			exchange.getResponseBody().write(out);
			exchange.close();
		});
		server.createContext("/xrpc/com.atproto.repo.createRecord", (exchange) -> {
			recordHits.incrementAndGet();
			recordAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			recordBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(recordStatus, -1);
			exchange.close();
		});
		server.start();
	}

	private BlueskyNotifier<Evt> notifier(String base, List<String> ignoreChanges) {
		return new BlueskyNotifier<>(base, "alice.bsky.social", "app-pass-1234", HttpClientConfig.defaults(), Evt::id,
				Evt::status, Evt::message, ignoreChanges);
	}

	private String base() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void createsSessionThenPostsRecordWithTheJwt() {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		new BlueskyNotifier<Evt>(base, "alice.bsky.social", "app-pass-1234", HttpClientConfig.defaults(), Evt::id,
				Evt::status, Evt::message, List.of())
			.notify(new Evt(1, "FAILED", "build broke"));

		// step 1: session created with the identifier + app password
		assertThat(sessionBody.get()).contains("\"identifier\":\"alice.bsky.social\"")
			.contains("\"password\":\"app-pass-1234\"");
		// step 2: a post record, authorized with the returned JWT, carrying the message
		assertThat(recordAuth.get()).isEqualTo("Bearer jwt-123");
		assertThat(recordBody.get()).contains("\"repo\":\"did:plc:abc\"")
			.contains("\"collection\":\"app.bsky.feed.post\"")
			.contains("\"$type\":\"app.bsky.feed.post\"")
			.contains("\"text\":\"build broke\"")
			.contains("\"createdAt\":");
	}

	@Test
	void sessionFailureIsSwallowedAndNoRecordPosted() {
		server.removeContext("/xrpc/com.atproto.server.createSession");
		server.createContext("/xrpc/com.atproto.server.createSession", (exchange) -> {
			exchange.sendResponseHeaders(401, -1);
			exchange.close();
		});
		String base = "http://127.0.0.1:" + server.getAddress().getPort();

		// a failing channel must never break the caller
		assertThatCode(() -> new BlueskyNotifier<Evt>(base, "alice", "pw", HttpClientConfig.defaults(), Evt::id,
				Evt::status, Evt::message, List.of())
			.notify(new Evt(2, "FAILED", "x"))).doesNotThrowAnyException();
		assertThat(recordBody.get()).isNull(); // never reached createRecord
	}

	@Test
	void escapesSpecialCharactersInThePost() {
		notifier(base(), List.of()).notify(new Evt(3, "FAILED", "a\"b\nc\td\\e"));
		assertThat(recordBody.get()).contains("\\\"").contains("\\n").contains("\\t").contains("\\\\");
	}

	@Test
	void missingSessionFieldsAreSwallowed() {
		server.removeContext("/xrpc/com.atproto.server.createSession");
		server.createContext("/xrpc/com.atproto.server.createSession", (exchange) -> {
			byte[] out = "{\"did\":\"x\"}".getBytes(StandardCharsets.UTF_8); // no
																				// accessJwt
			exchange.sendResponseHeaders(200, out.length);
			exchange.getResponseBody().write(out);
			exchange.close();
		});
		assertThatCode(() -> notifier(base(), List.of()).notify(new Evt(4, "FAILED", "x"))).doesNotThrowAnyException();
		assertThat(recordHits.get()).isZero();
	}

	@Test
	void recordFailureIsSwallowed() {
		recordStatus = 500;
		assertThatCode(() -> notifier(base(), List.of()).notify(new Evt(5, "FAILED", "x"))).doesNotThrowAnyException();
		assertThat(recordHits.get()).isEqualTo(1);
	}

	@Test
	void connectionErrorIsSwallowed() throws Exception {
		int deadPort;
		try (ServerSocket s = new ServerSocket(0)) {
			deadPort = s.getLocalPort();
		}
		assertThatCode(() -> notifier("http://127.0.0.1:" + deadPort, List.of()).notify(new Evt(6, "FAILED", "x")))
			.doesNotThrowAnyException();
	}

	@Test
	void forgetTransitionResetsDedup() {
		BlueskyNotifier<Evt> n = notifier(base(), List.of());
		n.notify(new Evt(7, "FAILED", "x")); // delivered (transition)
		n.notify(new Evt(7, "FAILED", "x")); // suppressed (same status)
		assertThat(recordHits.get()).isEqualTo(1);
		n.forgetTransition(7L);
		n.notify(new Evt(7, "FAILED", "x")); // delivered again after reset
		assertThat(recordHits.get()).isEqualTo(2);
	}

	private record Evt(long id, String status, String message) {
	}

}
