package org.alexmond.notify4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.alexmond.notify4j.internal.WhatsAppNotifier;

/**
 * Built-in {@link ChannelCatalog}, backed by a per-scheme registry that mirrors the
 * {@code NotifierUrlParser} grammar. Each {@link Spec} owns its fields plus an
 * {@code assemble} (fields → URL remainder) and {@code disassemble} (remainder → fields)
 * that are inverses; a round-trip test pins them against the live parser so the catalog
 * cannot drift. The {@code +http}/{@code +https} transport and {@code ?tags=} are handled
 * here (never per-channel), and a {@code URL}-typed field is normalised so it can be
 * pasted with or without its {@code http(s)://} prefix. Package-private; reached via
 * {@link ChannelCatalog#standard()}.
 */
final class StandardChannelCatalog implements ChannelCatalog {

	static final StandardChannelCatalog INSTANCE = new StandardChannelCatalog();

	private final Map<String, Spec> specs = registry();

	@Override
	public List<ChannelDescriptor> catalog() {
		List<ChannelDescriptor> out = new ArrayList<>(specs.size());
		for (Spec s : specs.values()) {
			out.add(s.descriptor());
		}
		return List.copyOf(out);
	}

	@Override
	public Optional<ChannelDescriptor> describe(String scheme) {
		Spec s = (scheme != null) ? specs.get(scheme.toLowerCase(Locale.ROOT)) : null;
		return Optional.ofNullable(s).map(Spec::descriptor);
	}

	@Override
	public String buildUrl(String scheme, Map<String, String> values) {
		return buildUrl(scheme, values, Set.of(), false);
	}

	@Override
	public String buildUrl(String scheme, Map<String, String> values, Set<String> tags, boolean cleartextHttp) {
		Spec spec = spec(scheme);
		boolean[] forceCleartext = { false };
		Map<String, String> v = normalizeUrlFields(spec, (values != null) ? values : Map.of(), forceCleartext);
		return assemble(spec, v, tags, cleartextHttp || forceCleartext[0]);
	}

	@Override
	public String buildUrl(ParsedChannel channel) {
		if (channel == null) {
			throw new IllegalArgumentException("null channel");
		}
		Spec spec = spec(channel.scheme());
		for (ChannelField f : spec.fields()) {
			if (f.secret() && MASKED_SECRET.equals(channel.values().get(f.key()))) {
				throw new IllegalArgumentException("secret field '" + f.key()
						+ "' is still masked; use recompose(priorUrl, editedValues) to keep the existing secret");
			}
		}
		return buildUrl(channel.scheme(), channel.values(), channel.tags(), channel.cleartextHttp());
	}

	@Override
	public String recompose(String priorUrl, Map<String, String> editedValues) {
		Decomposed prior = decompose(priorUrl);
		Map<String, String> edited = (editedValues != null) ? editedValues : Map.of();
		Map<String, String> merged = new LinkedHashMap<>();
		for (ChannelField f : prior.spec().fields()) {
			String e = edited.get(f.key());
			String kept = prior.raw().get(f.key());
			String chosen = (e == null || (f.secret() && MASKED_SECRET.equals(e))) ? kept : e;
			if (chosen != null) {
				merged.put(f.key(), chosen);
			}
		}
		return buildUrl(prior.scheme(), merged, prior.tags(), prior.cleartextHttp());
	}

	@Override
	public List<ChannelValidationError> validate(String scheme, Map<String, String> values) {
		Spec spec = (scheme != null) ? specs.get(scheme.toLowerCase(Locale.ROOT)) : null;
		if (spec == null) {
			return List.of(new ChannelValidationError(null, "unknown_scheme", "unknown notification scheme"));
		}
		Map<String, String> v = (values != null) ? values : Map.of();
		List<ChannelValidationError> errors = new ArrayList<>();
		for (ChannelField f : spec.fields()) {
			String value = v.get(f.key());
			if (value == null || value.isBlank()) {
				if (f.required()) {
					errors.add(new ChannelValidationError(f.key(), "required", f.key() + " is required"));
				}
			}
			else if (f.type() == FieldType.URL && !looksLikeUrl(value)) {
				errors.add(new ChannelValidationError(f.key(), "invalid_url", f.key() + " is not a valid URL"));
			}
		}
		return List.copyOf(errors);
	}

	/**
	 * A light format check for a {@code URL}-typed field: a pasted transport is allowed
	 * (it is normalised on assembly), but the remainder must be a whitespace-free
	 * authority with a host — enough for a UI's "valid" to mean more than "non-empty".
	 */
	private static boolean looksLikeUrl(String value) {
		String v = value;
		if (v.regionMatches(true, 0, "https://", 0, 8)) {
			v = v.substring(8);
		}
		else if (v.regionMatches(true, 0, "http://", 0, 7)) {
			v = v.substring(7);
		}
		if (v.isBlank() || v.chars().anyMatch(Character::isWhitespace)) {
			return false;
		}
		try {
			return URI.create("https://" + v).getHost() != null;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	@Override
	public Optional<ParsedChannel> tryParse(String url) {
		try {
			return Optional.of(parse(url));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	@Override
	public ParsedChannel parse(String url) {
		Decomposed d = decompose(url);
		Map<String, String> out = new LinkedHashMap<>();
		for (ChannelField f : d.spec().fields()) {
			String value = d.raw().get(f.key());
			if (value == null) {
				continue; // absent optional field
			}
			out.put(f.key(), (f.secret() && !value.isEmpty()) ? MASKED_SECRET : value);
		}
		return new ParsedChannel(d.scheme(), out, d.tags(), d.cleartextHttp());
	}

	@Override
	public String redact(String url) {
		return AbstractHttpNotifier.redact(url);
	}

	// --- assembly / decomposition --------------------------------------------

	private String assemble(Spec spec, Map<String, String> values, Set<String> tags, boolean cleartextHttp) {
		String rest = spec.assemble().apply(values);
		StringBuilder url = new StringBuilder(spec.scheme());
		if (cleartextHttp) {
			url.append("+http");
		}
		url.append("://").append(rest);
		if (tags != null && !tags.isEmpty()) {
			url.append((rest.indexOf('?') >= 0) ? '&' : '?').append("tags=").append(String.join(",", tags));
		}
		return url.toString();
	}

	/**
	 * Strip a pasted transport from {@code URL}-typed fields so a provider webhook works
	 * verbatim: {@code https://host/…} → {@code host/…}; {@code http://host/…} →
	 * {@code host/…} and flags the {@code +http} transport via {@code forceCleartext}.
	 */
	private Map<String, String> normalizeUrlFields(Spec spec, Map<String, String> values, boolean[] forceCleartext) {
		Map<String, String> out = new LinkedHashMap<>(values);
		for (ChannelField f : spec.fields()) {
			if (f.type() != FieldType.URL) {
				continue;
			}
			String v = out.get(f.key());
			if (v == null) {
				continue;
			}
			if (v.regionMatches(true, 0, "https://", 0, 8)) {
				out.put(f.key(), v.substring(8));
			}
			else if (v.regionMatches(true, 0, "http://", 0, 7)) {
				out.put(f.key(), v.substring(7));
				forceCleartext[0] = true;
			}
		}
		return out;
	}

	private Decomposed decompose(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("blank notification url");
		}
		int sep = url.indexOf("://");
		if (sep < 0) {
			throw new IllegalArgumentException("notification url missing scheme");
		}
		String schemePart = url.substring(0, sep).toLowerCase(Locale.ROOT);
		String scheme = schemePart;
		boolean cleartextHttp = false;
		int plus = schemePart.indexOf('+');
		if (plus >= 0) {
			scheme = schemePart.substring(0, plus);
			cleartextHttp = "http".equals(schemePart.substring(plus + 1));
		}
		Spec spec = spec(scheme);
		Set<String> tags = new LinkedHashSet<>();
		String rest = extractTags(url.substring(sep + 3), tags);
		return new Decomposed(scheme, spec, spec.disassemble().apply(rest), tags, cleartextHttp);
	}

	private Spec spec(String scheme) {
		Spec spec = (scheme != null) ? specs.get(scheme.toLowerCase(Locale.ROOT)) : null;
		if (spec == null) {
			throw new IllegalArgumentException("unknown notification scheme");
		}
		return spec;
	}

	// --- registry ------------------------------------------------------------

	private static Map<String, Spec> registry() {
		Map<String, Spec> m = new LinkedHashMap<>();
		registerWebhooks(m);
		registerInteractive(m);
		registerMessaging(m);
		registerSocialAndIncident(m);
		return m;
	}

	private static void registerWebhooks(Map<String, Spec> m) {
		webhook(m, "slack", "Slack", "https://api.slack.com/messaging/webhooks");
		webhook(m, "teams", "Microsoft Teams",
				"https://learn.microsoft.com/microsoftteams/platform/webhooks-and-connectors/how-to/add-incoming-webhook");
		webhook(m, "discord", "Discord", "https://support.discord.com/hc/en-us/articles/228383668");
		webhook(m, "mattermost", "Mattermost", "https://developers.mattermost.com/integrate/webhooks/incoming/");
		webhook(m, "rocketchat", "Rocket.Chat", "https://docs.rocket.chat/docs/integrations");
		webhook(m, "googlechat", "Google Chat", "https://developers.google.com/chat/how-tos/webhooks");
		webhook(m, "webhook", "Webhook", null);
	}

	private static void registerInteractive(Map<String, Spec> m) {
		m.put("telegram",
				new Spec("telegram", "Telegram", "https://core.telegram.org/bots", List.of(
						field("host", FieldType.TEXT, true, false, "API host", "Telegram Bot API host.",
								"api.telegram.org"),
						field("token", FieldType.TEXT, true, true, "Bot token", "Token issued by @BotFather.", null),
						field("chatId", FieldType.TEXT, true, false, "Chat ID", "Target chat or channel id.",
								"123456789")),
						(v) -> g(v, "host") + "/" + g(v, "token") + "/" + g(v, "chatId"),
						StandardChannelCatalog::disTelegram));
		m.put("ntfy", new Spec("ntfy", "ntfy", "https://docs.ntfy.sh/",
				List.of(field("host", FieldType.TEXT, true, false, "Server host", "ntfy server host.", "ntfy.sh"),
						field("topic", FieldType.TEXT, true, false, "Topic", "Topic to publish to.", "alerts")),
				(v) -> g(v, "host") + "/" + g(v, "topic"), (rest) -> hostThen(rest, "topic")));
		m.put("gotify",
				new Spec("gotify", "Gotify", "https://gotify.net/docs/pushmsg", List.of(
						field("host", FieldType.TEXT, true, false, "Server host", "Gotify server host.",
								"gotify.example.com"),
						field("appToken", FieldType.TEXT, true, true, "App token", "Gotify application token.", null)),
						(v) -> g(v, "host") + "/" + g(v, "appToken"), (rest) -> hostThen(rest, "appToken")));
		m.put("pushover", new Spec("pushover", "Pushover", "https://pushover.net/api",
				List.of(field("appToken", FieldType.TEXT, true, true, "Application token",
						"Pushover application token.", null),
						field("userKey", FieldType.TEXT, true, true, "User key", "Pushover user or group key.", null)),
				(v) -> g(v, "appToken") + "/" + g(v, "userKey"), (rest) -> two(rest, "/", "appToken", "userKey")));
	}

	private static void registerMessaging(Map<String, Spec> m) {
		m.put("twilio", new Spec("twilio", "Twilio SMS", "https://www.twilio.com/docs/sms", List.of(
				field("sid", FieldType.TEXT, true, false, "Account SID", "Twilio account SID.", "ACxxxxxxxx"),
				field("authToken", FieldType.TEXT, true, true, "Auth token", "Twilio auth token.", null),
				field("from", FieldType.TEXT, true, false, "From number", "Sending number (E.164).", "+15551234567"),
				field("to", FieldType.TEXT, true, false, "To number", "Recipient number (E.164).", "+15557654321")),
				(v) -> g(v, "sid") + ":" + g(v, "authToken") + "@" + g(v, "from") + "/" + g(v, "to"),
				StandardChannelCatalog::disTwilio));
		m.put("signal", new Spec("signal", "Signal", "https://github.com/bbernhard/signal-cli-rest-api", List.of(
				field("host", FieldType.TEXT, true, false, "REST host", "signal-cli-rest-api host.",
						"signal.example.com"),
				field("from", FieldType.TEXT, true, false, "From number", "Registered sender (E.164).", "+15551234567"),
				field("to", FieldType.TEXT, true, false, "To number", "Recipient number (E.164).", "+15557654321")),
				(v) -> g(v, "host") + "/" + g(v, "from") + "/" + g(v, "to"), StandardChannelCatalog::disSignal));
		m.put("whatsapp",
				new Spec("whatsapp", "WhatsApp", "https://developers.facebook.com/docs/whatsapp/cloud-api",
						List.of(field("token", FieldType.TEXT, true, true, "Access token",
								"WhatsApp Cloud API access token.", null),
								field("phoneId", FieldType.TEXT, true, false, "Phone number ID",
										"WhatsApp Cloud API phone number id.", "1234567890"),
								field("to", FieldType.TEXT, true, false, "To number", "Recipient number (E.164).",
										"+15557654321"),
								field("version", FieldType.TEXT, false, false, "Graph API version",
										"Graph API version override (default " + WhatsAppNotifier.API_VERSION + ").",
										WhatsAppNotifier.API_VERSION)),
						StandardChannelCatalog::asmWhatsapp, StandardChannelCatalog::disWhatsapp));
		m.put("zulip", new Spec("zulip", "Zulip", "https://zulip.com/api/send-message",
				List.of(field("botEmail", FieldType.TEXT, true, false, "Bot email", "Zulip bot email address.",
						"bot@zulip.example.com"),
						field("apiKey", FieldType.TEXT, true, true, "API key", "Zulip bot API key.", null),
						field("host", FieldType.TEXT, true, false, "Server host", "Zulip server host.",
								"zulip.example.com"),
						field("stream", FieldType.TEXT, true, false, "Stream", "Target stream name.", "alerts"),
						field("topic", FieldType.TEXT, true, false, "Topic", "Topic within the stream.", "notify4j")),
				(v) -> g(v, "botEmail") + ":" + g(v, "apiKey") + "@" + g(v, "host") + "/" + g(v, "stream") + "/"
						+ g(v, "topic"),
				StandardChannelCatalog::disZulip));
	}

	private static void registerSocialAndIncident(Map<String, Spec> m) {
		m.put("matrix", new Spec("matrix", "Matrix", "https://matrix.org/docs/", List.of(
				field("token", FieldType.TEXT, true, true, "Access token", "Matrix access token.", null),
				field("host", FieldType.TEXT, true, false, "Homeserver host", "Matrix homeserver host.", "matrix.org"),
				field("roomId", FieldType.TEXT, true, false, "Room ID", "Target room id.", "!abc123:matrix.org")),
				(v) -> g(v, "token") + "@" + g(v, "host") + "/" + g(v, "roomId"), StandardChannelCatalog::disMatrix));
		m.put("mastodon", new Spec("mastodon", "Mastodon", "https://docs.joinmastodon.org/client/token/", List.of(
				field("token", FieldType.TEXT, true, true, "Access token", "Mastodon application access token.", null),
				field("host", FieldType.TEXT, true, false, "Instance host", "Mastodon instance host.",
						"mastodon.social")),
				(v) -> g(v, "token") + "@" + g(v, "host"), (rest) -> two(rest, "@", "token", "host")));
		m.put("bluesky",
				new Spec("bluesky", "Bluesky", "https://atproto.com",
						List.of(field("identifier", FieldType.TEXT, true, false, "Handle or DID",
								"Bluesky handle or DID.", "alice.bsky.social"),
								field("appPassword", FieldType.TEXT, true, true, "App password",
										"Bluesky app password (not the account password).", null),
								field("host", FieldType.TEXT, false, false, "PDS host",
										"Personal Data Server host (default bsky.social).", "bsky.social")),
						StandardChannelCatalog::asmBluesky, StandardChannelCatalog::disBluesky));
		m.put("pagerduty",
				credentialOnly("pagerduty", "PagerDuty", "https://developer.pagerduty.com/docs/events-api-v2/overview/",
						"routingKey", "Routing key", "Events API v2 integration routing key."));
		m.put("opsgenie",
				credentialOnly("opsgenie", "Opsgenie", "https://support.atlassian.com/opsgenie/docs/api-integration/",
						"apiKey", "API key", "Opsgenie API integration key."));
		m.put("pushbullet", credentialOnly("pushbullet", "Pushbullet", "https://docs.pushbullet.com/", "accessToken",
				"Access token", "Pushbullet access token."));
	}

	/** Webhook-family scheme: the whole URL is the (secret) endpoint. */
	private static void webhook(Map<String, Spec> m, String scheme, String displayName, String docsUrl) {
		// the generic "webhook" channel names no provider, so avoid "…issued by Webhook"
		String description = "webhook".equals(scheme) ? "Incoming webhook URL (any service that accepts a JSON POST)."
				: "Incoming webhook URL issued by " + displayName + ".";
		m.put(scheme,
				new Spec(scheme, displayName, docsUrl,
						List.of(field("url", FieldType.URL, true, true, "Webhook URL", description, null)),
						(v) -> g(v, "url"), (rest) -> map("url", rest)));
	}

	/**
	 * A credential-only scheme ({@code scheme://<secret>}); the default host is elided.
	 */
	private static Spec credentialOnly(String scheme, String displayName, String docsUrl, String key, String label,
			String description) {
		return new Spec(scheme, displayName, docsUrl,
				List.of(field(key, FieldType.TEXT, true, true, label, description, null)), (v) -> g(v, key), (rest) -> {
					URI u = URI.create("https://" + rest);
					String cred = (u.getUserInfo() != null) ? u.getUserInfo() : u.getHost();
					return map(key, (cred != null) ? cred : rest);
				});
	}

	// --- per-channel disassemble helpers (mirror NotifierUrlParser) -----------

	private static Map<String, String> disTelegram(String rest) {
		URI u = URI.create("https://" + rest);
		String[] seg = u.getPath().replaceFirst("^/", "").split("/");
		return map("host", str(u.getAuthority()), "token", seg(seg, 0), "chatId", seg(seg, 1));
	}

	private static Map<String, String> hostThen(String rest, String secondKey) {
		URI u = URI.create("https://" + rest);
		return map("host", str(u.getAuthority()), secondKey, seg(u.getPath().replaceFirst("^/", "").split("/"), 0));
	}

	private static Map<String, String> disTwilio(String rest) {
		int at = rest.indexOf('@');
		String[] sidToken = ((at < 0) ? rest : rest.substring(0, at)).split(":", 2);
		String[] fromTo = ((at < 0) ? "" : rest.substring(at + 1)).split("/");
		return map("sid", seg(sidToken, 0), "authToken", seg(sidToken, 1), "from", seg(fromTo, 0), "to",
				seg(fromTo, 1));
	}

	private static Map<String, String> disSignal(String rest) {
		int slash = rest.indexOf('/');
		String host = (slash < 0) ? rest : rest.substring(0, slash);
		String[] seg = ((slash < 0) ? "" : rest.substring(slash + 1)).split("/");
		return map("host", host, "from", seg(seg, 0), "to", seg(seg, 1));
	}

	private static String asmWhatsapp(Map<String, String> v) {
		String base = g(v, "token") + "@" + g(v, "phoneId") + "/" + g(v, "to");
		String version = g(v, "version");
		return (!version.isEmpty() && !version.equals(WhatsAppNotifier.API_VERSION)) ? base + "?version=" + version
				: base;
	}

	private static Map<String, String> disWhatsapp(String rest) {
		String version = queryParam(rest, "version");
		String body = stripQuery(rest);
		int at = body.indexOf('@');
		String token = (at < 0) ? body : body.substring(0, at);
		String[] idTo = ((at < 0) ? "" : body.substring(at + 1)).split("/");
		Map<String, String> m = map("token", token, "phoneId", seg(idTo, 0), "to", seg(idTo, 1));
		if (version != null && !version.isBlank()) {
			m.put("version", version);
		}
		return m;
	}

	private static Map<String, String> disZulip(String rest) {
		int at = rest.lastIndexOf('@');
		int colon = (at < 0) ? -1 : rest.lastIndexOf(':', at);
		String email = (colon < 0) ? "" : rest.substring(0, colon);
		String key = (colon < 0 || at < 0) ? "" : rest.substring(colon + 1, at);
		String[] hp = ((at < 0) ? "" : rest.substring(at + 1)).split("/");
		return map("botEmail", email, "apiKey", key, "host", seg(hp, 0), "stream", seg(hp, 1), "topic", seg(hp, 2));
	}

	private static String asmBluesky(Map<String, String> v) {
		String base = g(v, "identifier") + ":" + g(v, "appPassword");
		String host = g(v, "host");
		return (!host.isEmpty() && !"bsky.social".equals(host)) ? base + "@" + host : base;
	}

	private static Map<String, String> disBluesky(String rest) {
		int at = rest.indexOf('@');
		String creds = (at < 0) ? rest : rest.substring(0, at);
		int colon = creds.indexOf(':');
		String identifier = (colon < 0) ? creds : creds.substring(0, colon);
		String appPassword = (colon < 0) ? "" : creds.substring(colon + 1);
		Map<String, String> m = map("identifier", identifier, "appPassword", appPassword);
		if (at >= 0 && !rest.substring(at + 1).isEmpty()) {
			m.put("host", rest.substring(at + 1)); // only when an explicit host was given
		}
		return m;
	}

	private static Map<String, String> disMatrix(String rest) {
		int at = rest.indexOf('@');
		String token = (at < 0) ? "" : rest.substring(0, at);
		String rem = (at < 0) ? rest : rest.substring(at + 1);
		int slash = rem.indexOf('/');
		String host = (slash < 0) ? rem : rem.substring(0, slash);
		String roomId = (slash < 0) ? "" : rem.substring(slash + 1);
		return map("token", token, "host", host, "roomId", roomId);
	}

	// --- small utilities -----------------------------------------------------

	private static ChannelField field(String key, FieldType type, boolean required, boolean secret, String label,
			String description, String example) {
		return new ChannelField(key, type, required, secret, label, description, example);
	}

	private static String g(Map<String, String> values, String key) {
		String v = values.get(key);
		return (v != null) ? v : "";
	}

	private static String str(String s) {
		return (s != null) ? s : "";
	}

	private static String seg(String[] arr, int i) {
		return (i < arr.length) ? arr[i] : "";
	}

	private static Map<String, String> two(String rest, String sep, String firstKey, String secondKey) {
		int at = rest.indexOf(sep);
		String first = (at < 0) ? rest : rest.substring(0, at);
		String second = (at < 0) ? "" : rest.substring(at + sep.length());
		return map(firstKey, first, secondKey, second);
	}

	private static Map<String, String> map(String... kv) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	/** Pull {@code tags=} into {@code tags}, return the remainder (other query kept). */
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

	private static String stripQuery(String rest) {
		int q = rest.indexOf('?');
		return (q < 0) ? rest : rest.substring(0, q);
	}

	/** A decomposed URL with its scheme, spec, raw (unmasked) fields, tags, transport. */
	private record Decomposed(String scheme, Spec spec, Map<String, String> raw, Set<String> tags,
			boolean cleartextHttp) {
	}

	/** One channel's metadata plus the inverse assemble/disassemble pair. */
	private record Spec(String scheme, String displayName, String docsUrl, List<ChannelField> fields,
			Function<Map<String, String>, String> assemble, Function<String, Map<String, String>> disassemble) {

		boolean credentialBearing() {
			return fields.stream().anyMatch(ChannelField::secret);
		}

		ChannelDescriptor descriptor() {
			return new ChannelDescriptor(scheme, displayName, fields, credentialBearing(), docsUrl);
		}
	}

}
