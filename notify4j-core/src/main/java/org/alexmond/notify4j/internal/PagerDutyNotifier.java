package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;
import org.alexmond.notify4j.Severity;

import java.util.List;
import java.util.function.Function;

/**
 * Triggers an incident via the PagerDuty Events API v2 ({@code POST
 * {endpoint}/v2/enqueue} with a {@code routing_key} + {@code event_action:"trigger"}
 * envelope). The id becomes the {@code dedup_key} so repeated failures for the same
 * subject coalesce into one incident. No auth header — the routing key is carried in the
 * body.
 *
 * @param <E> the application's event type
 */
public class PagerDutyNotifier<E> extends AbstractHttpNotifier<E> {

	private final String routingKey;

	/**
	 * @param endpoint the Events API base (e.g. {@code https://events.pagerduty.com});
	 * {@code /v2/enqueue} is appended.
	 */
	public PagerDutyNotifier(String endpoint, String routingKey, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, Function<E, String> titleFn,
			Function<E, Severity> severityFn, List<String> ignoreChanges) {
		super(endpoint + "/v2/enqueue", httpConfig, idFn, statusFn, messageFn, titleFn, severityFn, ignoreChanges);
		this.routingKey = routingKey;
	}

	@Override
	protected String payload(E event) {
		return "{\"routing_key\":" + jsonString(routingKey) + ",\"event_action\":\"trigger\"" + ",\"dedup_key\":"
				+ jsonValue(idFn.apply(event)) + ",\"payload\":{" + "\"summary\":" + jsonString(messageFn.apply(event))
				+ ",\"source\":\"notify4j\"" + ",\"severity\":" + jsonString(pagerDutySeverity(severityFn.apply(event)))
				+ ",\"custom_details\":{\"status\":" + jsonString(statusFn.apply(event)) + "}" + "}}";
	}

	/** Map onto PagerDuty's severities; {@code DEFAULT} stays {@code error} as before. */
	private static String pagerDutySeverity(Severity severity) {
		return switch (severity) {
			case INFO -> "info";
			case WARNING -> "warning";
			case CRITICAL -> "critical";
			case ERROR, DEFAULT -> "error";
		};
	}

}
