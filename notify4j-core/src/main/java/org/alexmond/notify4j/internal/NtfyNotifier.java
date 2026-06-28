package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;
import org.alexmond.notify4j.Severity;

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
			Function<E, String> statusFn, Function<E, String> messageFn, Function<E, String> titleFn,
			Function<E, Severity> severityFn, List<String> ignoreChanges) {
		super(baseUrl, httpConfig, idFn, statusFn, messageFn, titleFn, severityFn, ignoreChanges);
		this.topic = topic;
	}

	@Override
	protected String payload(E event) {
		Integer priority = ntfyPriority(severityFn.apply(event));
		String priorityField = (priority != null) ? ",\"priority\":" + jsonValue(priority) : "";
		return "{\"topic\":" + jsonString(topic) + ",\"title\":"
				+ jsonString(resolvedTitle(event, statusFn.apply(event))) + ",\"message\":"
				+ jsonString(messageFn.apply(event)) + priorityField + "}";
	}

	/** Map onto ntfy's 1–5 priority; {@code DEFAULT} omits it (ntfy default 3). */
	private static Integer ntfyPriority(Severity severity) {
		return switch (severity) {
			case INFO -> 2;
			case WARNING -> 3;
			case ERROR -> 4;
			case CRITICAL -> 5;
			case DEFAULT -> null;
		};
	}

}
