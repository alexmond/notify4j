package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Posts a status (toot) via the
 * <a href="https://docs.joinmastodon.org/methods/statuses/">Mastodon API</a> ({@code POST
 * {baseUrl}/api/v1/statuses} with a {@code Bearer} access token and
 * {@code {"status":…}}). {@code baseUrl} is the instance host.
 *
 * @param <E> the application's event type
 */
public class MastodonNotifier<E> extends AbstractHttpNotifier<E> {

	private final String authHeader;

	public MastodonNotifier(String baseUrl, String accessToken, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/api/v1/statuses", httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.authHeader = "Bearer " + accessToken;
	}

	@Override
	protected Map<String, String> headers() {
		return Map.of("Authorization", this.authHeader);
	}

	@Override
	protected String payload(E event) {
		return "{\"status\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
