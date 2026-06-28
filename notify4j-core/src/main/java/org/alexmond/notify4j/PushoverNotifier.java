package org.alexmond.notify4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pushes to the <a href="https://pushover.net">Pushover</a> Messages API ({@code POST
 * {baseUrl}/1/messages.json}, form-encoded with {@code token}, {@code user},
 * {@code title}, {@code message}). The application API token and user/group key are
 * carried in the URL; the status becomes the title and the message the body.
 * {@code baseUrl} is configurable for testing.
 *
 * @param <E> the application's event type
 */
public class PushoverNotifier<E> extends AbstractHttpNotifier<E> {

	private final String token;

	private final String user;

	public PushoverNotifier(String baseUrl, String token, String user, HttpClientConfig httpConfig,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			Function<E, String> titleFn, Function<E, Severity> severityFn, List<String> ignoreChanges) {
		super(baseUrl + "/1/messages.json", httpConfig, idFn, statusFn, messageFn, titleFn, severityFn, ignoreChanges);
		this.token = token;
		this.user = user;
	}

	@Override
	protected String contentType() {
		return "application/x-www-form-urlencoded";
	}

	@Override
	protected String payload(E event) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("token", this.token);
		fields.put("user", this.user);
		fields.put("title", resolvedTitle(event, statusFn.apply(event)));
		fields.put("message", messageFn.apply(event));
		// null priority is skipped by formEncode (DEFAULT → Pushover's own default of 0).
		fields.put("priority", pushoverPriority(severityFn.apply(event)));
		return formEncode(fields);
	}

	/**
	 * Map onto Pushover's -2..2 priority; {@code DEFAULT} omits it (Pushover default 0).
	 */
	private static String pushoverPriority(Severity severity) {
		return switch (severity) {
			case INFO -> "-1";
			case WARNING -> "0";
			case ERROR -> "1";
			case CRITICAL -> "2";
			case DEFAULT -> null;
		};
	}

}
