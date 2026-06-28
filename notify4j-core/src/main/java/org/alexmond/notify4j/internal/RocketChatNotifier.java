package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;

import java.util.List;
import java.util.function.Function;

/**
 * Posts to a Rocket.Chat incoming webhook ({@code {"text": ...}}).
 *
 * @param <E> the application's event type
 */
public class RocketChatNotifier<E> extends AbstractHttpNotifier<E> {

	public RocketChatNotifier(String webhookUrl, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(webhookUrl, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"text\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
