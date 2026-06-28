package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;

import java.util.List;
import java.util.function.Function;

/**
 * Sends a message via a self-hosted
 * <a href="https://github.com/bbernhard/signal-cli-rest-api"> signal-cli REST</a> bridge
 * ({@code POST {baseUrl}/v2/send} with
 * {@code {"number":<from>,"recipients":[<to>],"message":…}}). {@code baseUrl} is the
 * bridge (use a {@code +http} transport for a local bridge).
 *
 * @param <E> the application's event type
 */
public class SignalNotifier<E> extends AbstractHttpNotifier<E> {

	private final String from;

	private final String to;

	public SignalNotifier(String baseUrl, String from, String to, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/v2/send", httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.from = from;
		this.to = to;
	}

	@Override
	protected String payload(E event) {
		return "{\"number\":" + jsonString(this.from) + ",\"recipients\":[" + jsonString(this.to) + "],\"message\":"
				+ jsonString(messageFn.apply(event)) + "}";
	}

}
