package org.alexmond.notify4j;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HTTP settings for the webhook-style channels: a single {@link HttpClient}
 * (reused across every channel rather than one client per channel) plus the per-request
 * read timeout. The connect timeout is baked into the client when it is built.
 *
 * <p>
 * The Spring Boot starter builds one of these from {@code notify4j.http.*} and threads it
 * through to the channels; callers that don't configure HTTP get {@link #defaults()} (10s
 * connect, 10s read).
 */
public record HttpClientConfig(HttpClient client, Duration requestTimeout) {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

	private static final HttpClientConfig DEFAULT = of(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);

	/**
	 * Process-wide default (10s connect + 10s read), shared by callers that don't
	 * configure HTTP.
	 */
	public static HttpClientConfig defaults() {
		return DEFAULT;
	}

	/** Build a config with the given connect timeout and per-request read timeout. */
	public static HttpClientConfig of(Duration connectTimeout, Duration requestTimeout) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		return new HttpClientConfig(client, requestTimeout);
	}

}
