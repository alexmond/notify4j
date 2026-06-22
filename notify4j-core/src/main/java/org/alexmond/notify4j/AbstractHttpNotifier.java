package org.alexmond.notify4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base for HTTP/webhook channel notifiers. Generic over the event type and configured
 * with functions (no subclass-per-event needed): the app supplies how to read the id,
 * status, and human message from its event. Only meaningful status transitions are
 * delivered (see {@link TransitionFilter}). Uses the JDK {@link HttpClient} so this stays
 * Spring-free and library-reusable; the client and timeouts come from a shared
 * {@link HttpClientConfig}.
 */
public abstract class AbstractHttpNotifier<E> extends AbstractEventNotifier<E> {

	private static final long MAX_BACKOFF_MS = 30_000L;

	private static final int MAX_BACKOFF_SHIFT = 16;

	private final HttpClientConfig httpConfig;

	private final String url;

	private final TransitionFilter filter;

	protected final Function<E, Object> idFn;

	protected final Function<E, String> statusFn;

	protected final Function<E, String> messageFn;

	protected AbstractHttpNotifier(String url, HttpClientConfig httpConfig, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		this.url = url;
		this.httpConfig = httpConfig;
		this.idFn = idFn;
		this.statusFn = statusFn;
		this.messageFn = messageFn;
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

	@Override
	protected boolean shouldNotify(E event) {
		return filter.allow(idFn.apply(event), statusFn.apply(event));
	}

	@Override
	protected void doNotify(E event) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.header("Content-Type", "application/json")
			.timeout(httpConfig.requestTimeout())
			.POST(HttpRequest.BodyPublishers.ofString(payload(event), StandardCharsets.UTF_8));
		headers().forEach(builder::header);
		HttpRequest request = builder.build();
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
					backoff(attempt);
					continue;
				}
				throw new IllegalStateException("HTTP " + code + " from " + url + ": " + resp.body());
			}
			catch (IOException ex) {
				if (attempt < maxAttempts) {
					backoff(attempt);
					continue;
				}
				throw new IllegalStateException("failed to POST to " + url + " after " + maxAttempts + " attempt(s)",
						ex);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted posting to " + url, ex);
			}
		}
	}

	/** Sleep for an exponentially increasing, capped delay before the next retry. */
	private void backoff(int attempt) {
		long base = httpConfig.retryBackoff().toMillis();
		long delay = Math.min(base << Math.min(attempt - 1, MAX_BACKOFF_SHIFT), MAX_BACKOFF_MS);
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted during retry backoff to " + url, ex);
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

}
