package org.alexmond.notify4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Posts a stream message via the <a href="https://zulip.com/api/send-message">Zulip
 * API</a> ({@code POST {baseUrl}/api/v1/messages}, form-encoded
 * {@code type=stream}/{@code to}/ {@code topic}/{@code content} with HTTP Basic auth from
 * the bot email + API key). The message becomes the content; {@code baseUrl} is the Zulip
 * host (cloud or self-hosted).
 *
 * @param <E> the application's event type
 */
public class ZulipNotifier<E> extends AbstractHttpNotifier<E> {

	private final String stream;

	private final String topic;

	private final String authHeader;

	public ZulipNotifier(String baseUrl, String botEmail, String apiKey, String stream, String topic,
			HttpClientConfig httpConfig, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/api/v1/messages", httpConfig, idFn, statusFn, messageFn, ignoreChanges);
		this.stream = stream;
		this.topic = topic;
		this.authHeader = "Basic "
				+ Base64.getEncoder().encodeToString((botEmail + ":" + apiKey).getBytes(StandardCharsets.UTF_8));
	}

	@Override
	protected Map<String, String> headers() {
		return Map.of("Authorization", this.authHeader);
	}

	@Override
	protected String contentType() {
		return "application/x-www-form-urlencoded";
	}

	@Override
	protected String payload(E event) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("type", "stream");
		fields.put("to", this.stream);
		fields.put("topic", this.topic);
		fields.put("content", messageFn.apply(event));
		return formEncode(fields);
	}

}
