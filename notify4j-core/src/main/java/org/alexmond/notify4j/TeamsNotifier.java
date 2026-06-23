package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Posts a <a href=
 * "https://learn.microsoft.com/microsoftteams/platform/webhooks-and-connectors/how-to/connectors-using">
 * MessageCard</a> to a Microsoft Teams incoming webhook. Use a <em>Power Automate
 * Workflows</em> webhook URL: Microsoft retired the legacy Office 365 connector webhooks
 * in May 2026, and the Workflows endpoint accepts MessageCard payloads (a bare
 * {@code {"text":…}} is not reliably rendered). The status becomes the card title/summary
 * and the message its body.
 */
public class TeamsNotifier<E> extends AbstractHttpNotifier<E> {

	public TeamsNotifier(String webhookUrl, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(webhookUrl, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	@Override
	protected String payload(E event) {
		String status = jsonString(statusFn.apply(event));
		return "{\"@type\":\"MessageCard\",\"@context\":\"https://schema.org/extensions\"" + ",\"summary\":" + status
				+ ",\"title\":" + status + ",\"text\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
