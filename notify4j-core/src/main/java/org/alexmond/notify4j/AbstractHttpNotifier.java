package org.alexmond.notify4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Base for HTTP/webhook channel notifiers. Generic over the event type and configured
 * with functions (no subclass-per-event needed): the app supplies how to read the id,
 * status, and human message from its event. Only meaningful status transitions are
 * delivered (see {@link TransitionFilter}). Uses the JDK {@link java.net.http.HttpClient}
 * so this stays Spring-free and library-reusable; the client, timeouts, and retry policy
 * come from a shared {@link HttpClientConfig}.
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public abstract class AbstractHttpNotifier<E> extends AbstractEventNotifier<E> {

	private static final long MAX_BACKOFF_MS = 30_000L;

	private static final int MAX_BACKOFF_SHIFT = 16;

	private final HttpClientConfig httpConfig;

	private final String url;

	/** {@link #url} with credentials stripped, for safe logging. */
	private final String safeUrl;

	private final TransitionFilter filter;

	protected final Function<E, Object> idFn;

	protected final Function<E, String> statusFn;

	protected final Function<E, String> messageFn;

	/** Optional title (may yield {@code null}); defaults to none. */
	protected final Function<E, String> titleFn;

	/** Optional severity (never {@code null}); defaults to {@link Severity#DEFAULT}. */
	protected final Function<E, Severity> severityFn;

	protected AbstractHttpNotifier(String url, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		this(url, httpConfig, idFn, statusFn, messageFn, (e) -> null, (e) -> Severity.DEFAULT, ignoreChanges);
	}

	/**
	 * Full constructor including the optional title and severity functions (used by
	 * channels that map them onto a native field). The other constructors default these
	 * to "no title" and {@link Severity#DEFAULT}.
	 */
	protected AbstractHttpNotifier(String url, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, Function<E, String> titleFn,
			Function<E, Severity> severityFn, List<String> ignoreChanges) {
		this.url = url;
		this.safeUrl = redact(url);
		this.httpConfig = httpConfig;
		this.idFn = idFn;
		this.statusFn = statusFn;
		this.messageFn = messageFn;
		this.titleFn = titleFn;
		this.severityFn = severityFn;
		this.filter = new TransitionFilter(ignoreChanges);
	}

	/**
	 * Convenience for custom channels that don't tune HTTP: uses
	 * {@link HttpClientConfig#defaults()}.
	 */
	protected AbstractHttpNotifier(String url, Function<E, Object> idFn, Function<E, String> statusFn,
			Function<E, String> messageFn, List<String> ignoreChanges) {
		this(url, HttpClientConfig.defaults(), idFn, statusFn, messageFn, ignoreChanges);
	}

	/**
	 * The title to use, or {@code fallback} when the application supplies none — so a
	 * channel that historically used the status as its title keeps that behaviour unless
	 * a title is set.
	 */
	protected String resolvedTitle(E event, String fallback) {
		String t = titleFn.apply(event);
		return (t != null) ? t : fallback;
	}

	@Override
	protected boolean shouldNotify(E event) {
		return filter.allow(idFn.apply(event), statusFn.apply(event));
	}

	@Override
	protected void forgetTransition(Object entityId) {
		filter.forget(entityId);
	}

	@Override
	protected void doNotify(E event) {
		HttpRequest request = buildRequest(event);
		if (httpConfig.nonBlockingRetry()) {
			// Async: launch and return immediately. Retries are scheduled off-thread, so
			// backoff never holds the (shared) delivery pool thread.
			sendAsyncWithRetry(request, 1);
		}
		else {
			// Synchronous: block the caller and sleep between retries.
			sendWithRetry(request);
		}
	}

	/**
	 * Async notifiers complete after {@link #doNotify} returns, so they record their own
	 * outcome (see {@link #onAsyncComplete}).
	 */
	@Override
	protected boolean deliversAsync() {
		return httpConfig.nonBlockingRetry();
	}

	/**
	 * The HTTP method for the request. Defaults to {@code POST} (the webhook shape). A
	 * channel whose API mandates a different verb overrides this — e.g. the Matrix
	 * Client-Server send endpoint requires {@code PUT}.
	 * @return the HTTP method name
	 * @since 1.1.1
	 */
	protected String httpMethod() {
		return "POST";
	}

	/**
	 * The absolute request URL for this delivery. Defaults to the fixed configured URL. A
	 * channel that must vary the URL per message overrides this — e.g. Matrix appends a
	 * unique transaction-id path segment. It runs once per delivery (inside
	 * {@link #buildRequest}), so an overriding id stays stable across retries and can act
	 * as the server-side idempotency key.
	 * @param event the event being delivered
	 * @return the URL to send to
	 * @since 1.1.1
	 */
	protected String requestUrl(E event) {
		return this.url;
	}

	private HttpRequest buildRequest(E event) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(requestUrl(event)))
			.header("Content-Type", contentType())
			.timeout(httpConfig.requestTimeout())
			.method(httpMethod(), HttpRequest.BodyPublishers.ofString(payload(event), StandardCharsets.UTF_8));
		headers().forEach(builder::header);
		return builder.build();
	}

	/**
	 * Blocking send with retry: throws on terminal failure (the caller swallows + logs).
	 */
	private void sendWithRetry(HttpRequest request) {
		int maxAttempts = httpConfig.maxAttempts();
		for (int attempt = 1;; attempt++) {
			try {
				HttpResponse<String> resp = httpConfig.client().send(request, HttpResponse.BodyHandlers.ofString());
				int code = resp.statusCode();
				if (code < 300) {
					return;
				}
				// retry transient server errors / rate limits; 4xx (except 429) won't
				// succeed on retry, so fail fast.
				if ((code == 429 || code >= 500) && attempt < maxAttempts) {
					sleepBackoff(attempt);
					continue;
				}
				throw new IllegalStateException("HTTP " + code + " from " + safeUrl + ": " + resp.body());
			}
			catch (IOException ex) {
				if (attempt < maxAttempts) {
					sleepBackoff(attempt);
					continue;
				}
				throw new IllegalStateException(
						"failed to POST to " + safeUrl + " after " + maxAttempts + " attempt(s)", ex);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted posting to " + safeUrl, ex);
			}
		}
	}

	/**
	 * Non-blocking send with retry: delivers via the async client and re-schedules
	 * transient failures after a delay without parking a thread. Records the outcome on
	 * completion.
	 */
	private void sendAsyncWithRetry(HttpRequest request, int attempt) {
		httpConfig.client()
			.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.whenComplete((resp, ex) -> onAsyncComplete(request, attempt, resp, ex));
	}

	private void onAsyncComplete(HttpRequest request, int attempt, HttpResponse<String> resp, Throwable ex) {
		int maxAttempts = httpConfig.maxAttempts();
		if (ex != null) {
			Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
			if (cause instanceof IOException && attempt < maxAttempts) {
				scheduleAsyncRetry(request, attempt);
			}
			else {
				recordDeliveryFailed();
				log.warn("notifier {} failed: POST to {} failed after {} attempt(s): {}", channelName(), safeUrl,
						attempt, cause.getMessage());
			}
			return;
		}
		int code = resp.statusCode();
		if (code < 300) {
			recordDelivered();
			return;
		}
		if ((code == 429 || code >= 500) && attempt < maxAttempts) {
			scheduleAsyncRetry(request, attempt);
		}
		else {
			recordDeliveryFailed();
			log.warn("notifier {} failed: HTTP {} from {}", channelName(), code, safeUrl);
		}
	}

	private void scheduleAsyncRetry(HttpRequest request, int attempt) {
		// Delay via the JDK's shared delayer (no dedicated thread parked), then
		// re-attempt.
		CompletableFuture.runAsync(() -> sendAsyncWithRetry(request, attempt + 1),
				CompletableFuture.delayedExecutor(backoffMillis(attempt), TimeUnit.MILLISECONDS));
	}

	/** Exponentially increasing, capped backoff (ms) before the next retry. */
	private long backoffMillis(int attempt) {
		long base = httpConfig.retryBackoff().toMillis();
		return Math.max(0L, Math.min(base << Math.min(attempt - 1, MAX_BACKOFF_SHIFT), MAX_BACKOFF_MS));
	}

	/** Sleep for the capped backoff before the next (blocking) retry. */
	private void sleepBackoff(int attempt) {
		try {
			Thread.sleep(backoffMillis(attempt));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted during retry backoff to " + safeUrl, ex);
		}
	}

	/** The channel-specific JSON body. */
	protected abstract String payload(E event);

	/**
	 * Extra request headers (e.g. an auth token). Empty by default; channels override as
	 * needed.
	 */
	protected Map<String, String> headers() {
		return Map.of();
	}

	/**
	 * The {@code Content-Type} of the POST body; {@code application/json} by default.
	 * Override (with a {@link #formEncode} body) for form-encoded channels such as
	 * Pushover or Twilio.
	 */
	protected String contentType() {
		return "application/json";
	}

	/**
	 * Encode fields as an {@code application/x-www-form-urlencoded} body. Null values are
	 * skipped.
	 */
	protected static String formEncode(Map<String, String> fields) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> field : fields.entrySet()) {
			if (field.getValue() == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('&');
			}
			sb.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	/** JSON-encode a string value (with surrounding quotes). */
	protected static String jsonString(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					}
					else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}

	/** JSON-encode a value: numbers verbatim, everything else as a string. */
	protected static String jsonValue(Object v) {
		if (v instanceof Number n) {
			return n.toString();
		}
		return jsonString((v != null) ? v.toString() : null);
	}

	/**
	 * Strip credentials from a URL for safe logging: keep scheme + host(:port) and mask
	 * the path/query/userinfo, since channel URLs carry secrets (a Telegram bot token in
	 * the path, a Gotify token in the query, or the whole opaque Slack/Discord webhook
	 * URL).
	 */
	static String redact(String url) {
		if (url == null) {
			return "<none>";
		}
		int sep = url.indexOf("://");
		if (sep >= 0) {
			String schemePart = url.substring(0, sep);
			int plus = schemePart.indexOf('+');
			String base = ((plus >= 0) ? schemePart.substring(0, plus) : schemePart).toLowerCase(Locale.ROOT);
			// Schemes whose authority IS the credential (pagerduty://<key>,
			// pushover://<app-token>/…): the secret parses as the URI host, so reflecting
			// the authority would leak it — mask to the scheme-only form.
			if (NotifierUrlParser.CREDENTIAL_SCHEMES.contains(base)) {
				return schemePart + "://…";
			}
		}
		try {
			URI u = URI.create(url);
			if (u.getHost() == null) {
				return "<redacted>";
			}
			StringBuilder sb = new StringBuilder();
			if (u.getScheme() != null) {
				sb.append(u.getScheme()).append("://");
			}
			sb.append(u.getHost());
			if (u.getPort() >= 0) {
				sb.append(':').append(u.getPort());
			}
			String path = u.getRawPath();
			if (path != null && !path.isEmpty() && !"/".equals(path)) {
				sb.append("/…");
			}
			return sb.toString();
		}
		catch (RuntimeException ex) {
			return "<redacted>";
		}
	}

}
