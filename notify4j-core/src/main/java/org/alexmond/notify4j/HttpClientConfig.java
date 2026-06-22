package org.alexmond.notify4j;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HTTP settings for the webhook-style channels: a single {@link HttpClient}
 * (reused across every channel rather than one client per channel), the per-request read
 * timeout, and the transient-failure retry policy ({@code maxAttempts} + base
 * {@code retryBackoff}). The connect timeout is baked into the client when it is built.
 *
 * <p>
 * The Spring Boot starter builds one of these from {@code notify4j.http.*} and threads it
 * through to the channels; callers that don't configure HTTP get {@link #defaults()} (10s
 * connect, 10s read, no retry).
 *
 * @param client the shared JDK HTTP client (connect timeout baked in)
 * @param requestTimeout per-request read timeout
 * @param maxAttempts total delivery attempts per send; {@code 1} disables retry
 * @param retryBackoff base backoff between retries (doubled each attempt, capped)
 */
public record HttpClientConfig(HttpClient client, Duration requestTimeout, int maxAttempts, Duration retryBackoff) {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(500);

	private static final HttpClientConfig DEFAULT = of(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);

	/**
	 * Process-wide default (10s connect + 10s read, single attempt / no retry), shared by
	 * callers that don't configure HTTP.
	 */
	public static HttpClientConfig defaults() {
		return DEFAULT;
	}

	/** Build a config with the given timeouts and no retry (single attempt). */
	public static HttpClientConfig of(Duration connectTimeout, Duration requestTimeout) {
		return of(connectTimeout, requestTimeout, 1, DEFAULT_BACKOFF);
	}

	/** Build a config with the given timeouts and retry policy. */
	public static HttpClientConfig of(Duration connectTimeout, Duration requestTimeout, int maxAttempts,
			Duration retryBackoff) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		return new HttpClientConfig(client, requestTimeout, Math.max(1, maxAttempts), retryBackoff);
	}

}
