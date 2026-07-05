package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sends a message to a room via the
 * <a href="https://spec.matrix.org/latest/client-server-api/">Matrix Client-Server
 * API</a>: {@code PUT
 * {baseUrl}/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}} with a
 * {@code Bearer} access token and {@code {"msgtype":"m.text","body":…}}. The transaction
 * id is generated once per delivery (so retries reuse it — Matrix treats it as the
 * idempotency key), and {@code baseUrl} is the homeserver.
 *
 * @param <E> the application's event type
 */
public class MatrixNotifier<E> extends AbstractHttpNotifier<E> {

	private final String authHeader;

	public MatrixNotifier(String baseUrl, String accessToken, String roomId, HttpClientConfig httpConfig,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			List<String> ignoreChanges) {
		super(baseUrl + "/_matrix/client/v3/rooms/" + URLEncoder.encode(roomId, StandardCharsets.UTF_8)
				+ "/send/m.room.message", httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.authHeader = "Bearer " + accessToken;
	}

	@Override
	protected String httpMethod() {
		return "PUT";
	}

	@Override
	protected String requestUrl(E event) {
		// Appends the {txnId}. buildRequest runs once per delivery, so the id is reused
		// across retries — a fresh id per retry would double-post.
		return super.requestUrl(event) + "/" + UUID.randomUUID();
	}

	@Override
	protected Map<String, String> headers() {
		return Map.of("Authorization", this.authHeader);
	}

	@Override
	protected String payload(E event) {
		return "{\"msgtype\":\"m.text\",\"body\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
