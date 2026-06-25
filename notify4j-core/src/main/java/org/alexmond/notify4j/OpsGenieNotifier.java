package org.alexmond.notify4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Creates an alert via the OpsGenie Alert API ({@code POST {endpoint}/v2/alerts} with a
 * {@code GenieKey} authorization header). The id becomes the {@code alias} so repeated
 * failures for the same subject de-duplicate into one alert.
 *
 * @param <E> the application's event type
 */
public class OpsGenieNotifier<E> extends AbstractHttpNotifier<E> {

	private final String apiKey;

	/**
	 * @param endpoint the Alert API base (e.g. {@code https://api.opsgenie.com});
	 * {@code /v2/alerts} is appended.
	 */
	public OpsGenieNotifier(String endpoint, String apiKey, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(endpoint + "/v2/alerts", httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.apiKey = apiKey;
	}

	@Override
	protected Map<String, String> headers() {
		return Map.of("Authorization", "GenieKey " + apiKey);
	}

	@Override
	protected String payload(E event) {
		return "{\"message\":" + jsonString(messageFn.apply(event)) + ",\"alias\":" + jsonValue(idFn.apply(event))
				+ ",\"description\":" + jsonString(messageFn.apply(event)) + ",\"details\":{\"status\":"
				+ jsonString(statusFn.apply(event)) + "}}";
	}

}
