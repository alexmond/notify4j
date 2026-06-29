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
 * here (never per-channel). Package-private; reached via
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
		String rest = spec.assemble().apply((values != null) ? values : Map.of());
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
			if (f.required() && (value == null || value.isBlank())) {
				errors.add(new ChannelValidationError(f.key(), "required", f.key() + " is required"));
			}
		}
		return List.copyOf(errors);
	}

	@Override
	public ParsedChannel parse(String url) {
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

		Map<String, String> raw = spec.disassemble().apply(rest);
		Map<String, String> out = new LinkedHashMap<>();
		for (ChannelField f : spec.fields()) {
			String value = raw.get(f.key());
			if (value == null) {
				continue; // absent optional field
			}
			out.put(f.key(), (f.secret() && !value.isEmpty()) ? MASKED_SECRET : value);
		}
		return new ParsedChannel(scheme, out, tags, cleartextHttp);
	}

	@Override
	public String redact(String url) {
		return AbstractHttpNotifier.redact(url);
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
		// Webhook-family: the whole URL is the (secret) endpoint.
		for (String scheme : List.of("slack", "teams", "discord", "mattermost", "rocketchat", "googlechat",
				"webhook")) {
			m.put(scheme, new Spec(scheme, List.of(field("url", FieldType.URL, true, true)), (v) -> g(v, "url"),
					(rest) -> map("url", rest)));
		}
		m.put("telegram", new Spec("telegram",
				List.of(field("host", FieldType.TEXT, true, false), field("token", FieldType.TEXT, true, true),
						field("chatId", FieldType.TEXT, true, false)),
				(v) -> g(v, "host") + "/" + g(v, "token") + "/" + g(v, "chatId"), StandardChannelCatalog::disTelegram));
		m.put("ntfy",
				new Spec("ntfy",
						List.of(field("host", FieldType.TEXT, true, false),
								field("topic", FieldType.TEXT, true, false)),
						(v) -> g(v, "host") + "/" + g(v, "topic"), (rest) -> hostThen(rest, "topic")));
		m.put("gotify",
				new Spec("gotify",
						List.of(field("host", FieldType.TEXT, true, false),
								field("appToken", FieldType.TEXT, true, true)),
						(v) -> g(v, "host") + "/" + g(v, "appToken"), (rest) -> hostThen(rest, "appToken")));
		m.put("pushover", new Spec("pushover",
				List.of(field("appToken", FieldType.TEXT, true, true), field("userKey", FieldType.TEXT, true, true)),
				(v) -> g(v, "appToken") + "/" + g(v, "userKey"), (rest) -> two(rest, "/", "appToken", "userKey")));
		m.put("twilio", new Spec("twilio",
				List.of(field("sid", FieldType.TEXT, true, false), field("authToken", FieldType.TEXT, true, true),
						field("from", FieldType.TEXT, true, false), field("to", FieldType.TEXT, true, false)),
				(v) -> g(v, "sid") + ":" + g(v, "authToken") + "@" + g(v, "from") + "/" + g(v, "to"),
				StandardChannelCatalog::disTwilio));
		m.put("signal", new Spec("signal",
				List.of(field("host", FieldType.TEXT, true, false), field("from", FieldType.TEXT, true, false),
						field("to", FieldType.TEXT, true, false)),
				(v) -> g(v, "host") + "/" + g(v, "from") + "/" + g(v, "to"), StandardChannelCatalog::disSignal));
		m.put("whatsapp", new Spec("whatsapp",
				List.of(field("token", FieldType.TEXT, true, true), field("phoneId", FieldType.TEXT, true, false),
						field("to", FieldType.TEXT, true, false), field("version", FieldType.TEXT, false, false)),
				StandardChannelCatalog::asmWhatsapp, StandardChannelCatalog::disWhatsapp));
		m.put("zulip",
				new Spec("zulip", List.of(field("botEmail", FieldType.TEXT, true, false),
						field("apiKey", FieldType.TEXT, true, true), field("host", FieldType.TEXT, true, false),
						field("stream", FieldType.TEXT, true, false), field("topic", FieldType.TEXT, true, false)),
						(v) -> g(v, "botEmail") + ":" + g(v, "apiKey") + "@" + g(v, "host") + "/" + g(v, "stream") + "/"
								+ g(v, "topic"),
						StandardChannelCatalog::disZulip));
		m.put("matrix", new Spec("matrix",
				List.of(field("token", FieldType.TEXT, true, true), field("host", FieldType.TEXT, true, false),
						field("roomId", FieldType.TEXT, true, false)),
				(v) -> g(v, "token") + "@" + g(v, "host") + "/" + g(v, "roomId"), StandardChannelCatalog::disMatrix));
		m.put("mastodon",
				new Spec("mastodon",
						List.of(field("token", FieldType.TEXT, true, true), field("host", FieldType.TEXT, true, false)),
						(v) -> g(v, "token") + "@" + g(v, "host"), (rest) -> two(rest, "@", "token", "host")));
		m.put("bluesky",
				new Spec("bluesky", List.of(field("identifier", FieldType.TEXT, true, false),
						field("appPassword", FieldType.TEXT, true, true), field("host", FieldType.TEXT, false, false)),
						StandardChannelCatalog::asmBluesky, StandardChannelCatalog::disBluesky));
		m.put("pagerduty", credentialOnly("pagerduty", "routingKey"));
		m.put("opsgenie", credentialOnly("opsgenie", "apiKey"));
		m.put("pushbullet", credentialOnly("pushbullet", "accessToken"));
		return m;
	}

	/**
	 * A credential-only scheme ({@code scheme://<secret>}); the default host is elided.
	 */
	private static Spec credentialOnly(String scheme, String key) {
		return new Spec(scheme, List.of(field(key, FieldType.TEXT, true, true)), (v) -> g(v, key), (rest) -> {
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

	private static ChannelField field(String key, FieldType type, boolean required, boolean secret) {
		return new ChannelField(key, type, required, secret);
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

	/** One channel's fields plus the inverse assemble/disassemble pair. */
	private record Spec(String scheme, List<ChannelField> fields, Function<Map<String, String>, String> assemble,
			Function<String, Map<String, String>> disassemble) {

		boolean credentialBearing() {
			return fields.stream().anyMatch(ChannelField::secret);
		}

		ChannelDescriptor descriptor() {
			return new ChannelDescriptor(scheme, fields, credentialBearing());
		}
	}

}
