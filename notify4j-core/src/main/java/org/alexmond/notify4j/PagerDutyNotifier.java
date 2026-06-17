package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Triggers an incident via the PagerDuty Events API v2 ({@code POST
 * {endpoint}/v2/enqueue} with a {@code routing_key} + {@code event_action:"trigger"}
 * envelope). The id becomes the {@code dedup_key} so repeated failures for the same
 * subject coalesce into one incident. No auth header — the routing key is carried in the
 * body.
 */
public class PagerDutyNotifier<E> extends AbstractHttpNotifier<E> {

	private final String routingKey;

	/**
	 * @param endpoint the Events API base (e.g. {@code https://events.pagerduty.com});
	 * {@code /v2/enqueue} is appended.
	 */
	public PagerDutyNotifier(String endpoint, String routingKey, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		super(endpoint + "/v2/enqueue", idFn, statusFn, messageFn, ignoreChanges);
		this.routingKey = routingKey;
	}

	@Override
	protected String payload(E event) {
		return "{\"routing_key\":" + jsonString(routingKey) + ",\"event_action\":\"trigger\"" + ",\"dedup_key\":"
				+ jsonValue(idFn.apply(event)) + ",\"payload\":{" + "\"summary\":" + jsonString(messageFn.apply(event))
				+ ",\"source\":\"notify4j\"" + ",\"severity\":\"error\"" + ",\"custom_details\":{\"status\":"
				+ jsonString(statusFn.apply(event)) + "}" + "}}";
	}

}
