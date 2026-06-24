package org.alexmond.notify4j;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns an Apprise/shoutrrr-style channel URL into a {@link Channel} (a {@link Notifier}
 * plus its routing tags). The <em>scheme</em> selects the channel (and therefore the
 * payload shape); an optional {@code +http}/{@code +https} transport suffix selects the
 * wire protocol (default {@code https}; {@code http} is mainly for tests). The remainder
 * of the URL is the literal endpoint. An optional {@code ?tags=a,b} query restricts which
 * events reach the channel (see
 * {@link Notifications#send(Object, java.util.Collection)}); it is stripped before the
 * endpoint is built. Examples:
 *
 * <pre>
 *   slack://hooks.slack.com/services/T000/B000/XXXX
 *   teams+http://127.0.0.1:8080/hook
 *   discord://discord.com/api/webhooks/ID/TOKEN
 *   mattermost://my-host/hooks/&lt;key&gt;
 *   rocketchat://my-host/hooks/&lt;id&gt;/&lt;token&gt;
 *   googlechat://chat.googleapis.com/v1/spaces/&lt;space&gt;/messages?key=&lt;k&gt;&amp;token=&lt;t&gt;
 *   webhook://my-host/notify
 *   telegram://api.telegram.org/&lt;bot-token&gt;/&lt;chat-id&gt;
 *   ntfy://ntfy.sh/&lt;topic&gt;
 *   gotify://my-host/&lt;app-token&gt;
 *   pushover://&lt;app-token&gt;/&lt;user-key&gt;
 *   pagerduty://&lt;routing-key&gt;?tags=failed
 *   opsgenie://&lt;api-key&gt;?tags=failed
 * </pre>
 *
 * Spring-free: the {@code spring-boot-starter} only supplies the URL list and the
 * adapter.
 *
 * @param <E> the application's event type
 */
public class NotifierUrlParser<E> {

	private final Function<E, Object> idFn;

	private final Function<E, String> statusFn;

	private final Function<E, String> messageFn;

	private final List<String> ignoreChanges;

	private final HttpClientConfig httpConfig;

	public NotifierUrlParser(NotificationAdapter<E> adapter, List<String> ignoreChanges) {
		this(adapter, ignoreChanges, HttpClientConfig.defaults());
	}

	public NotifierUrlParser(NotificationAdapter<E> adapter, List<String> ignoreChanges, HttpClientConfig httpConfig) {
		this.idFn = adapter::id;
		this.statusFn = adapter::status;
		this.messageFn = adapter::message;
		this.ignoreChanges = ignoreChanges;
		this.httpConfig = httpConfig;
	}

	/** Parse a single channel URL into a notifier plus its routing tags. */
	public Channel<E> parse(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("blank notification url");
		}
		int sep = url.indexOf("://");
		if (sep < 0) {
			throw new IllegalArgumentException("notification url missing scheme: " + url);
		}
		String scheme = url.substring(0, sep).toLowerCase(Locale.ROOT);

		String channel = scheme;
		String transport = "https";
		int plus = scheme.indexOf('+');
		if (plus >= 0) {
			channel = scheme.substring(0, plus);
			transport = scheme.substring(plus + 1);
		}
		if (!"http".equals(transport) && !"https".equals(transport)) {
			throw new IllegalArgumentException("unsupported transport '" + transport + "' in url: " + url);
		}

		Set<String> tags = new LinkedHashSet<>();
		String rest = extractTags(url.substring(sep + 3), tags);

		String endpoint = transport + "://" + rest;
		Notifier<E> notifier = switch (channel) {
			case "slack" -> new SlackNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "teams" -> new TeamsNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "discord" -> new DiscordNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "mattermost" ->
				new MattermostNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "rocketchat" ->
				new RocketChatNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "googlechat" ->
				new GoogleChatNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "webhook" -> new WebhookNotifier<>(endpoint, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			case "telegram" -> telegram(transport, rest, url);
			case "ntfy" -> ntfy(transport, rest, url);
			case "gotify" -> gotify(transport, rest, url);
			case "pushover" -> pushover(transport, rest, url);
			case "pagerduty" -> {
				String[] ch = credentialAndHost(transport, rest, url, "events.pagerduty.com");
				yield new PagerDutyNotifier<>(ch[1], ch[0], httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			}
			case "opsgenie" -> {
				String[] ch = credentialAndHost(transport, rest, url, "api.opsgenie.com");
				yield new OpsGenieNotifier<>(ch[1], ch[0], httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			}
			default ->
				throw new IllegalArgumentException("unknown notification scheme '" + channel + "' in url: " + url);
		};
		return new Channel<>(notifier, Set.copyOf(tags));
	}

	/**
	 * Pull the {@code tags} query param into {@code tags} and return {@code rest} with it
	 * removed.
	 */
	private static String extractTags(String rest, Set<String> tags) {
		int q = rest.indexOf('?');
		if (q < 0) {
			return rest;
		}
		StringBuilder kept = new StringBuilder();
		for (String pair : rest.substring(q + 1).split("&")) {
			int eq = pair.indexOf('=');
			String key = (eq < 0) ? pair : pair.substring(0, eq);
			if ("tags".equals(key) && eq >= 0) {
				for (String t : pair.substring(eq + 1).split(",")) {
					if (!t.isBlank()) {
						tags.add(t.trim());
					}
				}
			}
			else if (!pair.isBlank()) {
				kept.append(kept.isEmpty() ? "" : "&").append(pair);
			}
		}
		String base = rest.substring(0, q);
		return kept.isEmpty() ? base : base + "?" + kept;
	}

	private Notifier<E> telegram(String transport, String rest, String url) {
		URI u = URI.create(transport + "://" + rest);
		String authority = u.getAuthority();
		String path = u.getPath();
		if (authority == null || path == null) {
			throw new IllegalArgumentException("invalid telegram url: " + url);
		}
		String[] seg = path.replaceFirst("^/", "").split("/");
		if (seg.length < 2 || seg[0].isBlank() || seg[1].isBlank()) {
			throw new IllegalArgumentException("telegram url needs /<bot-token>/<chat-id>: " + url);
		}
		String baseUrl = transport + "://" + authority;
		return new TelegramNotifier<>(baseUrl, seg[0], seg[1], httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	private Notifier<E> ntfy(String transport, String rest, String url) {
		URI u = URI.create(transport + "://" + rest);
		String authority = u.getAuthority();
		String path = u.getPath();
		if (authority == null || path == null) {
			throw new IllegalArgumentException("invalid ntfy url: " + url);
		}
		String topic = path.replaceFirst("^/", "").split("/")[0];
		if (topic.isBlank()) {
			throw new IllegalArgumentException("ntfy url needs /<topic>: " + url);
		}
		String baseUrl = transport + "://" + authority;
		return new NtfyNotifier<>(baseUrl, topic, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	private Notifier<E> gotify(String transport, String rest, String url) {
		URI u = URI.create(transport + "://" + rest);
		String authority = u.getAuthority();
		String path = u.getPath();
		if (authority == null || path == null) {
			throw new IllegalArgumentException("invalid gotify url: " + url);
		}
		String token = path.replaceFirst("^/", "").split("/")[0];
		if (token.isBlank()) {
			throw new IllegalArgumentException("gotify url needs /<app-token>: " + url);
		}
		String baseUrl = transport + "://" + authority;
		return new GotifyNotifier<>(baseUrl, token, httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	private Notifier<E> pushover(String transport, String rest, String url) {
		// pushover://<app-token>/<user-key> — both credentials, fixed API host.
		String[] parts = rest.split("/");
		if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("pushover url needs <app-token>/<user-key>: " + url);
		}
		String baseUrl = transport + "://api.pushover.net";
		return new PushoverNotifier<>(baseUrl, parts[0], parts[1], httpConfig, idFn, statusFn, messageFn,
				ignoreChanges);
	}

	/**
	 * For credential-carrying schemes ({@code scheme://<credential>[@<host>[:port]]}):
	 * returns {@code [credential, endpoint]}. With no explicit host the credential is the
	 * whole authority and the endpoint falls back to {@code defaultHost} (e.g.
	 * {@code pagerduty://<routing-key>}); with an explicit host the credential is the
	 * user-info part (e.g. {@code opsgenie+http://<key>@host:port} for tests).
	 */
	private String[] credentialAndHost(String transport, String rest, String url, String defaultHost) {
		URI u = URI.create(transport + "://" + rest);
		String userInfo = u.getUserInfo();
		String host = u.getHost();
		if (userInfo != null && !userInfo.isBlank()) {
			String authority = host + ((u.getPort() >= 0) ? ":" + u.getPort() : "");
			return new String[] { userInfo, transport + "://" + authority };
		}
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("missing credential in url: " + url);
		}
		return new String[] { host, transport + "://" + defaultHost };
	}

	/**
	 * A parsed channel: the notifier plus the tags that gate which events reach it (empty
	 * = all).
	 */
	public record Channel<E>(Notifier<E> notifier, Set<String> tags) {

		/**
		 * A channel with no tags fires for every event; a tagged channel fires only on an
		 * overlap.
		 */
		public boolean matches(java.util.Collection<String> routeTags) {
			if (tags.isEmpty()) {
				return true;
			}
			for (String t : routeTags) {
				if (tags.contains(t)) {
					return true;
				}
			}
			return false;
		}
	}

}
