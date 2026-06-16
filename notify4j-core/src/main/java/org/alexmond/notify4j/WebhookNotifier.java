package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Posts a generic JSON payload ({@code {"id":…,"status":…,"message":…}}) to an arbitrary
 * webhook.
 */
public class WebhookNotifier<E> extends AbstractHttpNotifier<E> {

	public WebhookNotifier(String url, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		super(url, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		return "{\"id\":" + jsonValue(idFn.apply(event)) + ",\"status\":" + jsonString(statusFn.apply(event))
				+ ",\"message\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
