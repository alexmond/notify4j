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
 * <p>
 * {@code nonBlockingRetry} controls how retries wait: when {@code false} (default) the
 * delivery blocks the calling thread and sleeps between attempts (fine for synchronous
 * use); when {@code true} the channel delivers via the async HTTP client and schedules
 * retries without holding a thread, so retry backoff never ties up the shared delivery
 * pool. The starter enables it whenever asynchronous delivery is on.
 *
 * @param client the shared JDK HTTP client (connect timeout baked in)
 * @param requestTimeout per-request read timeout
 * @param maxAttempts total delivery attempts per send; {@code 1} disables retry
 * @param retryBackoff base backoff between retries (doubled each attempt, capped)
 * @param nonBlockingRetry deliver asynchronously and schedule retries off-thread
 * @since 1.0.0
 */
public record HttpClientConfig(HttpClient client, Duration requestTimeout, int maxAttempts, Duration retryBackoff,
		boolean nonBlockingRetry) {

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

	/** Build a config with the given timeouts and retry policy (blocking retry). */
	public static HttpClientConfig of(Duration connectTimeout, Duration requestTimeout, int maxAttempts,
			Duration retryBackoff) {
		return of(connectTimeout, requestTimeout, maxAttempts, retryBackoff, false);
	}

	/** Build a config with the given timeouts, retry policy, and retry-wait mode. */
	public static HttpClientConfig of(Duration connectTimeout, Duration requestTimeout, int maxAttempts,
			Duration retryBackoff, boolean nonBlockingRetry) {
		// Redirects are pinned to NEVER (rather than relying on the JDK default) so a
		// credentialed POST can't be bounced by a 3xx to an attacker-chosen host —
		// channel requests carry secrets in headers/body. Do not relax this.
		// Pin HTTP/1.1. The JDK client defaults to HTTP/2, whose cleartext
		// h2c upgrade some webhook servers (e.g. Rocket.Chat) mishandle — the
		// exchange then stalls until the request timeout and the send fails.
		// A lone POST gains nothing from HTTP/2 anyway.
		HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		return new HttpClientConfig(client, requestTimeout, Math.max(1, maxAttempts), retryBackoff, nonBlockingRetry);
	}

}
