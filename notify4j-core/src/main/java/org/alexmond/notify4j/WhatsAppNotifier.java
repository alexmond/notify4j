package org.alexmond.notify4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sends a WhatsApp text via the
 * <a href="https://developers.facebook.com/docs/whatsapp/cloud-api">Meta WhatsApp Cloud
 * API</a> ({@code POST {baseUrl}/{version}/{phone-number-id}/messages} with a
 * {@code Bearer} token and a {@code messaging_product:"whatsapp"} text envelope).
 * {@code baseUrl} is configurable for testing; the Graph API version is pinned by
 * {@link #API_VERSION}.
 *
 * @param <E> the application's event type
 */
public class WhatsAppNotifier<E> extends AbstractHttpNotifier<E> {

	/** Graph API version this channel targets; bump as Meta deprecates versions. */
	public static final String API_VERSION = "v21.0";

	private final String to;

	private final String authHeader;

	public WhatsAppNotifier(String baseUrl, String token, String phoneNumberId, String to, HttpClientConfig httpConfig,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			List<String> ignoreChanges) {
		super(baseUrl + "/" + API_VERSION + "/" + phoneNumberId + "/messages", httpConfig, idFn, statusFn, messageFn,
				ignoreChanges);
		this.to = to;
		this.authHeader = "Bearer " + token;
	}

	@Override
	protected Map<String, String> headers() {
		return Map.of("Authorization", this.authHeader);
	}

	@Override
	protected String payload(E event) {
		return "{\"messaging_product\":\"whatsapp\",\"to\":" + jsonString(this.to) + ",\"type\":\"text\",\"text\":{"
				+ "\"body\":" + jsonString(messageFn.apply(event)) + "}}";
	}

}
