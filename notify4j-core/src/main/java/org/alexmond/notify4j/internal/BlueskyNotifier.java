package org.alexmond.notify4j.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.alexmond.notify4j.AbstractEventNotifier;
import org.alexmond.notify4j.HttpClientConfig;
import org.alexmond.notify4j.TransitionFilter;

/**
 * Posts to <a href="https://atproto.com">Bluesky</a> via the AT Protocol. Unlike the
 * webhook channels this needs two calls: {@code com.atproto.server.createSession}
 * (identifier + app password → an access JWT and DID), then
 * {@code com.atproto.repo.createRecord} to create an {@code app.bsky.feed.post}. The app
 * password lives outside the URL host, so it never appears in logged endpoints.
 *
 * @param <E> the application's event type
 */
public class BlueskyNotifier<E> extends AbstractEventNotifier<E> {

	private static final String POST_TYPE = "app.bsky.feed.post";

	private final HttpClientConfig httpConfig;

	private final String baseUrl;

	private final String identifier;

	private final String appPassword;

	private final Function<E, Object> idFn;

	private final Function<E, String> statusFn;

	private final Function<E, String> messageFn;

	private final TransitionFilter filter;

	public BlueskyNotifier(String baseUrl, String identifier, String appPassword, HttpClientConfig httpConfig,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			List<String> ignoreChanges) {
		this.baseUrl = baseUrl;
		this.identifier = identifier;
		this.appPassword = appPassword;
		this.httpConfig = httpConfig;
		this.idFn = idFn;
		this.statusFn = statusFn;
		this.messageFn = messageFn;
		this.filter = new TransitionFilter(ignoreChanges);
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
		Session session = createSession();
		String record = "{\"$type\":\"" + POST_TYPE + "\",\"text\":" + json(messageFn.apply(event)) + ",\"createdAt\":"
				+ json(Instant.now().toString()) + "}";
		String body = "{\"repo\":" + json(session.did()) + ",\"collection\":\"" + POST_TYPE + "\",\"record\":" + record
				+ "}";
		HttpResponse<String> resp = post("/xrpc/com.atproto.repo.createRecord", body, "Bearer " + session.jwt());
		if (resp.statusCode() >= 300) {
			throw new IllegalStateException("Bluesky createRecord HTTP " + resp.statusCode() + " from " + baseUrl);
		}
	}

	private Session createSession() {
		String body = "{\"identifier\":" + json(identifier) + ",\"password\":" + json(appPassword) + "}";
		HttpResponse<String> resp = post("/xrpc/com.atproto.server.createSession", body, null);
		if (resp.statusCode() >= 300) {
			throw new IllegalStateException("Bluesky createSession HTTP " + resp.statusCode() + " from " + baseUrl);
		}
		String jwt = field(resp.body(), "accessJwt");
		String did = field(resp.body(), "did");
		if (jwt == null || did == null) {
			throw new IllegalStateException("Bluesky createSession returned no session from " + baseUrl);
		}
		return new Session(jwt, did);
	}

	private HttpResponse<String> post(String path, String body, String authorization) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.header("Content-Type", "application/json")
			.timeout(httpConfig.requestTimeout())
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		if (authorization != null) {
			builder.header("Authorization", authorization);
		}
		try {
			return httpConfig.client().send(builder.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Bluesky request to " + baseUrl + " failed", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted posting to " + baseUrl, ex);
		}
	}

	private static String field(String jsonBody, String key) {
		Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(jsonBody);
		return m.find() ? m.group(1) : null;
	}

	private static String json(String s) {
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

	private record Session(String jwt, String did) {
	}

}
