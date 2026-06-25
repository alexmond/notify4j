package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Publishes to a self-hosted <a href="https://gotify.net">Gotify</a> server: POST
 * {@code {baseUrl}/message?token={appToken}} with {@code {"title":…,"message":…}}, where
 * the title carries the status. {@code baseUrl} is configurable for self-hosting and
 * tests.
 *
 * @param <E> the application's event type
 */
public class GotifyNotifier<E> extends AbstractHttpNotifier<E> {

	public GotifyNotifier(String baseUrl, String appToken, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/message?token=" + appToken, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"title\":" + jsonString(statusFn.apply(event)) + ",\"message\":" + jsonString(messageFn.apply(event))
				+ "}";
	}

}
