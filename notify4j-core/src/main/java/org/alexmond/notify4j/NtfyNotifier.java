package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Publishes to an <a href="https://ntfy.sh">ntfy</a> topic. Uses ntfy's JSON-to-root
 * form: POST the {@code baseUrl} root with {@code {"topic":…,"message":…,"title":…}},
 * where the title carries the status. Works against ntfy.sh or a self-hosted server.
 *
 * @param <E> the application's event type
 */
public class NtfyNotifier<E> extends AbstractHttpNotifier<E> {

	private final String topic;

	public NtfyNotifier(String baseUrl, String topic, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.topic = topic;
	}

	@Override
	protected String payload(E event) {
		return "{\"topic\":" + jsonString(topic) + ",\"title\":" + jsonString(statusFn.apply(event)) + ",\"message\":"
				+ jsonString(messageFn.apply(event)) + "}";
	}

}
