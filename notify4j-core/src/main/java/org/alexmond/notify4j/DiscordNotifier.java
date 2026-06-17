package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/** Posts to a Discord webhook ({@code {"content": ...}}). */
public class DiscordNotifier<E> extends AbstractHttpNotifier<E> {

	public DiscordNotifier(String webhookUrl, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(webhookUrl, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"content\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
