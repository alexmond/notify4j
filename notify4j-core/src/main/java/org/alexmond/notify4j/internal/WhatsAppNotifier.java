package org.alexmond.notify4j.internal;

import org.alexmond.notify4j.AbstractHttpNotifier;
import org.alexmond.notify4j.HttpClientConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sends a WhatsApp text via the
 * <a href="https://developers.facebook.com/docs/whatsapp/cloud-api">Meta WhatsApp Cloud
 * API</a> ({@code POST {baseUrl}/{version}/{phone-number-id}/messages} with a
 * {@code Bearer} token and a {@code messaging_product:"whatsapp"} text envelope).
 * {@code baseUrl} is configurable for testing; the Graph API version defaults to
 * {@link #API_VERSION} but can be overridden (e.g. {@code whatsapp://…?version=v22.0}) as
 * Meta deprecates versions.
 *
 * @param <E> the application's event type
 */
public class WhatsAppNotifier<E> extends AbstractHttpNotifier<E> {

	/**
	 * Default Graph API version; override per-channel via the URL {@code version} param.
	 */
	public static final String API_VERSION = "v21.0";

	private final String to;

	private final String authHeader;

	/** Uses the default {@link #API_VERSION}. */
	public WhatsAppNotifier(String baseUrl, String token, String phoneNumberId, String to, HttpClientConfig httpConfig,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			List<String> ignoreChanges) {
		this(baseUrl, token, phoneNumberId, to, API_VERSION, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	/** With an explicit Graph API version (e.g. {@code v22.0}). */
	public WhatsAppNotifier(String baseUrl, String token, String phoneNumberId, String to, String apiVersion,
			HttpClientConfig httpConfig, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/" + apiVersion + "/" + phoneNumberId + "/messages", httpConfig, idFn, statusFn, messageFn,
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
