package org.alexmond.notify4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/xrpc/com.atproto.server.createSession", (exchange) -> {
			sessionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] out = "{\"accessJwt\":\"jwt-123\",\"did\":\"did:plc:abc\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, out.length);
			exchange.getResponseBody().write(out);
			exchange.close();
		});
		server.createContext("/xrpc/com.atproto.repo.createRecord", (exchange) -> {
			recordAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			recordBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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

	private record Evt(long id, String status, String message) {
	}

}
