package org.alexmond.notify4j;

import org.alexmond.notify4j.internal.DiscordNotifier;
import org.alexmond.notify4j.internal.GoogleChatNotifier;
import org.alexmond.notify4j.internal.GotifyNotifier;
import org.alexmond.notify4j.internal.MattermostNotifier;
import org.alexmond.notify4j.internal.NtfyNotifier;
import org.alexmond.notify4j.internal.OpsGenieNotifier;
import org.alexmond.notify4j.internal.PagerDutyNotifier;
import org.alexmond.notify4j.internal.PushbulletNotifier;
import org.alexmond.notify4j.internal.PushoverNotifier;
import org.alexmond.notify4j.internal.RocketChatNotifier;
import org.alexmond.notify4j.internal.SignalNotifier;
import org.alexmond.notify4j.internal.SlackNotifier;
import org.alexmond.notify4j.internal.TeamsNotifier;
import org.alexmond.notify4j.internal.TelegramNotifier;
import org.alexmond.notify4j.internal.TwilioNotifier;
import org.alexmond.notify4j.internal.WebhookNotifier;
import org.alexmond.notify4j.internal.WhatsAppNotifier;
import org.alexmond.notify4j.internal.ZulipNotifier;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   twilio://&lt;account-sid&gt;:&lt;auth-token&gt;@&lt;from&gt;/&lt;to&gt;
 *   signal://&lt;bridge-host&gt;/&lt;from&gt;/&lt;to&gt;
 *   whatsapp://&lt;token&gt;@&lt;phone-number-id&gt;/&lt;to&gt;?version=v22.0
 *   zulip://&lt;bot-email&gt;:&lt;api-key&gt;@&lt;host&gt;/&lt;stream&gt;/&lt;topic&gt;
 *   pushbullet://&lt;access-token&gt;
 *   pagerduty://&lt;routing-key&gt;?tags=failed
 *   opsgenie://&lt;api-key&gt;?tags=failed
 * </pre>
 *
 * <h2>Grammar (stable since 1.0.0)</h2>
 *
 * The URL grammar is part of the public API and is frozen; these rules will not change in
 * a backward-incompatible way within 1.x:
 *
 * <ul>
 * <li><b>Scheme</b> (before {@code ://}) selects the channel and payload shape; it is
 * lower-cased.</li>
 * <li><b>Transport suffix</b>: an optional {@code +http}/{@code +https} on the scheme
 * selects the wire protocol (default {@code https}). {@code +http} is intended for tests
 * and self-hosted endpoints; using it for a credential-bearing scheme logs a
 * warning.</li>
 * <li><b>{@code ?tags=a,b}</b> sets routing tags (comma-separated) and is removed from
 * the URL before the endpoint is built; other query parameters are preserved.</li>
 * <li><b>Credential-only schemes</b> ({@code pagerduty}, {@code opsgenie},
 * {@code pushbullet}, {@code pushover}) put the secret in the authority and fall back to
 * a fixed default host ({@code events.pagerduty.com}, {@code api.opsgenie.com},
 * {@code api.pushbullet.com}, {@code api.pushover.net}); an explicit {@code user@host}
 * form overrides the host (mainly for tests).</li>
 * <li><b>{@code zulip}</b> is parsed with the <em>last</em> {@code @} (the bot email
 * itself contains an {@code @}) and the <em>last</em> {@code :} before it.</li>
 * <li><b>{@code twilio}</b>/{@code whatsapp} use the <em>first</em> {@code @}; phone
 * numbers keep a leading {@code +}.</li>
 * <li><b>{@code whatsapp}</b> accepts an optional {@code ?version=} to override the Graph
 * API version (default {@link WhatsAppNotifier#API_VERSION}).</li>
 * </ul>
 *
 * Spring-free: the {@code spring-boot-starter} only supplies the URL list and the
 * adapter.
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public class NotifierUrlParser<E> {

	private static final Logger log = LoggerFactory.getLogger(NotifierUrlParser.class);

	/**
	 * Schemes whose URL embeds a secret (token / key / SID); used to warn when they are
	 * configured over cleartext {@code +http}, which would put that secret on the wire
	 * unencrypted.
	 */
	private static final Set<String> CREDENTIAL_SCHEMES = Set.of("telegram", "gotify", "pushover", "twilio", "whatsapp",
			"zulip", "pagerduty", "opsgenie", "pushbullet");

	private final Function<E, Object> idFn;

	private final Function<E, String> statusFn;

	private final Function<E, String> messageFn;

	private final Function<E, String> titleFn;

	private final Function<E, Severity> severityFn;

	private final List<String> ignoreChanges;

	private final HttpClientConfig httpConfig;

	/** Parser using default HTTP settings ({@link HttpClientConfig#defaults()}). */
	public NotifierUrlParser(NotificationAdapter<E> adapter, List<String> ignoreChanges) {
		this(adapter, ignoreChanges, HttpClientConfig.defaults());
	}

	/**
	 * Parser with explicit HTTP settings (shared client, timeouts, retry) for the
	 * channels.
	 */
	public NotifierUrlParser(NotificationAdapter<E> adapter, List<String> ignoreChanges, HttpClientConfig httpConfig) {
		this.idFn = adapter::id;
		this.statusFn = adapter::status;
		this.messageFn = adapter::message;
		this.titleFn = adapter::title;
		this.severityFn = adapter::severity;
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
			throw new IllegalArgumentException("notification url missing scheme: " + safe(url));
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
			throw new IllegalArgumentException("unsupported transport '" + transport + "' in url: " + safe(url));
		}
		if ("http".equals(transport) && CREDENTIAL_SCHEMES.contains(channel)) {
			log.warn("channel '{}' uses cleartext +http: its embedded credentials will be sent unencrypted — "
					+ "use https in production", channel);
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
			case "twilio" -> twilio(transport, rest, url);
			case "signal" -> signal(transport, rest, url);
			case "whatsapp" -> whatsapp(transport, rest, url);
			case "zulip" -> zulip(transport, rest, url);
			case "pushbullet" -> {
				String[] ch = credentialAndHost(transport, rest, url, "api.pushbullet.com");
				yield new PushbulletNotifier<>(ch[1], ch[0], httpConfig, idFn, statusFn, messageFn, ignoreChanges);
			}
			case "pagerduty" -> {
				String[] ch = credentialAndHost(transport, rest, url, "events.pagerduty.com");
				yield new PagerDutyNotifier<>(ch[1], ch[0], httpConfig, idFn, statusFn, messageFn, titleFn, severityFn,
						ignoreChanges);
			}
			case "opsgenie" -> {
				String[] ch = credentialAndHost(transport, rest, url, "api.opsgenie.com");
				yield new OpsGenieNotifier<>(ch[1], ch[0], httpConfig, idFn, statusFn, messageFn, titleFn, severityFn,
						ignoreChanges);
			}
			default -> throw new IllegalArgumentException(
					"unknown notification scheme '" + channel + "' in url: " + safe(url));
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

	/**
	 * Redact a channel URL for error messages. A malformed URL throws during facade
	 * construction (i.e. at application startup), so the message must not leak the secret
	 * into the boot log. Channel URLs carry secrets in many positions — a token in the
	 * path ({@code telegram}), the query ({@code gotify}), the user-info
	 * ({@code zulip}/{@code twilio}), or the authority itself ({@code pagerduty}/
	 * {@code pushover}/{@code pushbullet}) — so we keep only the scheme (which names the
	 * offending channel) and mask everything after it.
	 */
	private static String safe(String url) {
		if (url == null) {
			return "<none>";
		}
		int sep = url.indexOf("://");
		return (sep < 0) ? "<redacted>" : url.substring(0, sep) + "://…";
	}

	/**
	 * Read a single query-parameter value from {@code rest}, or {@code null} if absent.
	 * Used by hand-parsed schemes to pick up options (e.g. {@code version}) before
	 * {@link #stripQuery} drops the query.
	 */
	private static String queryParam(String rest, String key) {
		int q = rest.indexOf('?');
		if (q < 0) {
			return null;
		}
		for (String pair : rest.substring(q + 1).split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0 && key.equals(pair.substring(0, eq))) {
				return pair.substring(eq + 1);
			}
		}
		return null;
	}

	/**
	 * Drop any trailing query so it can't land inside a credential/target segment of the
	 * hand-parsed schemes (the URI-based ones ignore the query already).
	 */
	private static String stripQuery(String rest) {
		int q = rest.indexOf('?');
		return (q < 0) ? rest : rest.substring(0, q);
	}

	private Notifier<E> telegram(String transport, String rest, String url) {
		URI u = URI.create(transport + "://" + rest);
		String authority = u.getAuthority();
		String path = u.getPath();
		if (authority == null || path == null) {
			throw new IllegalArgumentException("invalid telegram url: " + safe(url));
		}
		String[] seg = path.replaceFirst("^/", "").split("/");
		if (seg.length < 2 || seg[0].isBlank() || seg[1].isBlank()) {
			throw new IllegalArgumentException("telegram url needs /<bot-token>/<chat-id>: " + safe(url));
		}
		String baseUrl = transport + "://" + authority;
		return new TelegramNotifier<>(baseUrl, seg[0], seg[1], httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	private Notifier<E> ntfy(String transport, String rest, String url) {
		URI u = URI.create(transport + "://" + rest);
		String authority = u.getAuthority();
		String path = u.getPath();
		if (authority == null || path == null) {
			throw new IllegalArgumentException("invalid ntfy url: " + safe(url));
		}
		String topic = path.replaceFirst("^/", "").split("/")[0];
		if (topic.isBlank()) {
			throw new IllegalArgumentException("ntfy url needs /<topic>: " + safe(url));
		}
		String baseUrl = transport + "://" + authority;
		return new NtfyNotifier<>(baseUrl, topic, httpConfig, idFn, statusFn, messageFn, titleFn, severityFn,
				ignoreChanges);
	}

	private Notifier<E> gotify(String transport, String rest, String url) {
		URI u = URI.create(transport + "://" + rest);
		String authority = u.getAuthority();
		String path = u.getPath();
		if (authority == null || path == null) {
			throw new IllegalArgumentException("invalid gotify url: " + safe(url));
		}
		String token = path.replaceFirst("^/", "").split("/")[0];
		if (token.isBlank()) {
			throw new IllegalArgumentException("gotify url needs /<app-token>: " + safe(url));
		}
		String baseUrl = transport + "://" + authority;
		return new GotifyNotifier<>(baseUrl, token, httpConfig, idFn, statusFn, messageFn, titleFn, severityFn,
				ignoreChanges);
	}

	private Notifier<E> pushover(String transport, String rest, String url) {
		// pushover://<app-token>/<user-key> — both credentials, fixed API host.
		String[] parts = stripQuery(rest).split("/");
		if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("pushover url needs <app-token>/<user-key>: " + safe(url));
		}
		String baseUrl = transport + "://api.pushover.net";
		return new PushoverNotifier<>(baseUrl, parts[0], parts[1], httpConfig, idFn, statusFn, messageFn, titleFn,
				severityFn, ignoreChanges);
	}

	private Notifier<E> twilio(String transport, String rest, String url) {
		// twilio://<account-sid>:<auth-token>@<from>/<to> — parsed by hand so '+' in
		// phone
		// numbers survives (URI host parsing would choke on it).
		rest = stripQuery(rest);
		int at = rest.indexOf('@');
		if (at < 0) {
			throw new IllegalArgumentException("twilio url needs <sid>:<token>@<from>/<to>: " + safe(url));
		}
		String[] sidToken = rest.substring(0, at).split(":", 2);
		String[] fromTo = rest.substring(at + 1).split("/");
		if (sidToken.length < 2 || fromTo.length < 2 || sidToken[0].isBlank() || sidToken[1].isBlank()
				|| fromTo[0].isBlank() || fromTo[1].isBlank()) {
			throw new IllegalArgumentException("twilio url needs <sid>:<token>@<from>/<to>: " + safe(url));
		}
		String baseUrl = transport + "://api.twilio.com";
		return new TwilioNotifier<>(baseUrl, sidToken[0], sidToken[1], fromTo[0], fromTo[1], httpConfig, idFn, statusFn,
				messageFn, ignoreChanges);
	}

	private Notifier<E> signal(String transport, String rest, String url) {
		// signal://<host>[:port]/<from>/<to>
		rest = stripQuery(rest);
		int slash = rest.indexOf('/');
		if (slash < 0) {
			throw new IllegalArgumentException("signal url needs <host>/<from>/<to>: " + safe(url));
		}
		String authority = rest.substring(0, slash);
		String[] seg = rest.substring(slash + 1).split("/");
		if (authority.isBlank() || seg.length < 2 || seg[0].isBlank() || seg[1].isBlank()) {
			throw new IllegalArgumentException("signal url needs <host>/<from>/<to>: " + safe(url));
		}
		String baseUrl = transport + "://" + authority;
		return new SignalNotifier<>(baseUrl, seg[0], seg[1], httpConfig, idFn, statusFn, messageFn, ignoreChanges);
	}

	private Notifier<E> whatsapp(String transport, String rest, String url) {
		// whatsapp://<token>@<phone-number-id>/<to>[?version=v22.0]
		String version = queryParam(rest, "version");
		rest = stripQuery(rest);
		int at = rest.indexOf('@');
		if (at < 0) {
			throw new IllegalArgumentException("whatsapp url needs <token>@<phone-id>/<to>: " + safe(url));
		}
		String token = rest.substring(0, at);
		String[] idTo = rest.substring(at + 1).split("/");
		if (token.isBlank() || idTo.length < 2 || idTo[0].isBlank() || idTo[1].isBlank()) {
			throw new IllegalArgumentException("whatsapp url needs <token>@<phone-id>/<to>: " + safe(url));
		}
		String baseUrl = transport + "://graph.facebook.com";
		String apiVersion = (version != null && !version.isBlank()) ? version : WhatsAppNotifier.API_VERSION;
		return new WhatsAppNotifier<>(baseUrl, token, idTo[0], idTo[1], apiVersion, httpConfig, idFn, statusFn,
				messageFn, ignoreChanges);
	}

	private Notifier<E> zulip(String transport, String rest, String url) {
		// zulip://<bot-email>:<api-key>@<host>/<stream>/<topic> — lastIndexOf('@')
		// because the
		// bot email itself contains '@'.
		rest = stripQuery(rest);
		int at = rest.lastIndexOf('@');
		int colon = (at < 0) ? -1 : rest.lastIndexOf(':', at);
		if (at < 0 || colon < 0) {
			throw new IllegalArgumentException(
					"zulip url needs <bot-email>:<api-key>@<host>/<stream>/<topic>: " + safe(url));
		}
		String email = rest.substring(0, colon);
		String key = rest.substring(colon + 1, at);
		String[] hostPath = rest.substring(at + 1).split("/");
		if (email.isBlank() || key.isBlank() || hostPath.length < 3 || hostPath[0].isBlank() || hostPath[1].isBlank()
				|| hostPath[2].isBlank()) {
			throw new IllegalArgumentException(
					"zulip url needs <bot-email>:<api-key>@<host>/<stream>/<topic>: " + safe(url));
		}
		String baseUrl = transport + "://" + hostPath[0];
		return new ZulipNotifier<>(baseUrl, email, key, hostPath[1], hostPath[2], httpConfig, idFn, statusFn, messageFn,
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
			throw new IllegalArgumentException("missing credential in url: " + safe(url));
		}
		return new String[] { host, transport + "://" + defaultHost };
	}

	/**
	 * A parsed channel: the notifier plus the tags that gate which events reach it (empty
	 * = all).
	 *
	 * @param <E> the application's event type
	 * @param notifier the notifier events are delivered to
	 * @param tags routing tags; empty means the channel fires for every event
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
