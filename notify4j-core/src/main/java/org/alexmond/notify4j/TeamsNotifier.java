package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/** Posts to a Microsoft Teams incoming webhook ({@code {"text": ...}}). */
public class TeamsNotifier<E> extends AbstractHttpNotifier<E> {

	public TeamsNotifier(String webhookUrl, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		super(webhookUrl, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"text\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
