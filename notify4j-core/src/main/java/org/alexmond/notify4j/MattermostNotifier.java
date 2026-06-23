package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/** Posts to a Mattermost incoming webhook ({@code {"text": ...}}). */
public class MattermostNotifier<E> extends AbstractHttpNotifier<E> {

	public MattermostNotifier(String webhookUrl, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(webhookUrl, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"text\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
