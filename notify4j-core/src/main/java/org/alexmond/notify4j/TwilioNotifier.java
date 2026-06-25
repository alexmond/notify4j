package org.alexmond.notify4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Sends an SMS via the <a href="https://www.twilio.com/docs/sms/api">Twilio Messages
 * API</a> ({@code POST {baseUrl}/2010-04-01/Accounts/{sid}/Messages.json}, form-encoded
 * {@code To}/{@code From}/{@code Body} with HTTP Basic auth). {@code baseUrl} is
 * configurable for testing.
 *
 * @param <E> the application's event type
 */
public class TwilioNotifier<E> extends AbstractHttpNotifier<E> {

	private final String from;

	private final String to;

	private final String authHeader;

	public TwilioNotifier(String baseUrl, String accountSid, String authToken, String from, String to,
			HttpClientConfig httpConfig, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/2010-04-01/Accounts/" + accountSid + "/Messages.json", httpConfig, idFn, statusFn, messageFn,
				ignoreChanges);
		this.from = from;
		this.to = to;
		this.authHeader = "Basic "
				+ Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
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
		fields.put("To", this.to);
		fields.put("From", this.from);
		fields.put("Body", messageFn.apply(event));
		return formEncode(fields);
	}

}
