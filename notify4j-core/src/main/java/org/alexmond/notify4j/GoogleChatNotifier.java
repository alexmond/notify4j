package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Posts to a Google Chat space via its incoming webhook ({@code {"text": ...}}). The
 * webhook's {@code key}/{@code token} query params are carried through on the endpoint
 * URL.
 *
 * @param <E> the application's event type
 */
public class GoogleChatNotifier<E> extends AbstractHttpNotifier<E> {

	public GoogleChatNotifier(String webhookUrl, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(webhookUrl, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"text\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
