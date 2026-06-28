package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pushes a note via the <a href="https://docs.pushbullet.com">Pushbullet API</a>
 * ({@code POST {endpoint}/v2/pushes} with an {@code Access-Token} header and
 * {@code {"type":"note","title":…,"body":…}}). The status becomes the title and the
 * message the body.
 *
 * @param <E> the application's event type
 */
public class PushbulletNotifier<E> extends AbstractHttpNotifier<E> {

	private final String accessToken;

	/**
	 * @param endpoint the API base (e.g. {@code https://api.pushbullet.com});
	 * {@code /v2/pushes} is appended.
	 */
	public PushbulletNotifier(String endpoint, String accessToken, HttpClientConfig httpConfig,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			List<String> ignoreChanges) {
		super(endpoint + "/v2/pushes", httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.accessToken = accessToken;
	}

	@Override
	protected Map<String, String> headers() {
		return Map.of("Access-Token", this.accessToken);
	}

	@Override
	protected String payload(E event) {
		return "{\"type\":\"note\",\"title\":" + jsonString(statusFn.apply(event)) + ",\"body\":"
				+ jsonString(messageFn.apply(event)) + "}";
	}

}
