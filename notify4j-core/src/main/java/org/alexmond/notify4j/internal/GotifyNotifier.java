package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;
import org.alexmond.notify4j.Severity;

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
			Function<E, String> statusFn, Function<E, String> messageFn, Function<E, String> titleFn,
			Function<E, Severity> severityFn, List<String> ignoreChanges) {
		super(baseUrl + "/message?token=" + appToken, httpConfig, idFn, statusFn, messageFn, titleFn, severityFn,
				ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		Integer priority = gotifyPriority(severityFn.apply(event));
		String priorityField = (priority != null) ? ",\"priority\":" + jsonValue(priority) : "";
		return "{\"title\":" + jsonString(resolvedTitle(event, statusFn.apply(event))) + ",\"message\":"
				+ jsonString(messageFn.apply(event)) + priorityField + "}";
	}

	/** Map onto Gotify's 0–10 priority; {@code DEFAULT} omits it (Gotify default 0). */
	private static Integer gotifyPriority(Severity severity) {
		return switch (severity) {
			case INFO -> 1;
			case WARNING -> 5;
			case ERROR -> 8;
			case CRITICAL -> 10;
			case DEFAULT -> null;
		};
	}

}
