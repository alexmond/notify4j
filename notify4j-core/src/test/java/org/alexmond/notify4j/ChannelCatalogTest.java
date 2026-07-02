package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.alexmond.notify4j.internal.WhatsAppNotifier;
import org.junit.jupiter.api.Test;

/**
 * Pins the channel catalog against the live {@link NotifierUrlParser}: every
 * {@code buildUrl} output must parse, and {@code parse} must round-trip non-secret fields
 * while masking secrets — including the nasty grammars (embedded {@code @}, literal
 * {@code +}, {@code :} room-id, default-host elision).
 */
class ChannelCatalogTest {

	private final ChannelCatalog catalog = ChannelCatalog.standard();

	private final NotifierUrlParser<String> parser = new NotifierUrlParser<>(adapter(), List.of());

	/** Representative field values per scheme, with deliberately tricky fixtures. */
	private static Map<String, Map<String, String>> samples() {
		Map<String, String> webhookish = Map.of("url", "example.com/services/A/B/SECRET");
		Map<String, Map<String, String>> s = new java.util.LinkedHashMap<>();
		for (String scheme : List.of("slack", "teams", "discord", "mattermost", "rocketchat", "googlechat",
				"webhook")) {
			s.put(scheme, webhookish);
		}
		s.put("telegram", Map.of("host", "api.telegram.org", "token", "BOT-SECRET", "chatId", "42"));
		s.put("ntfy", Map.of("host", "ntfy.sh", "topic", "alerts"));
		s.put("gotify", Map.of("host", "gotify.example.com", "appToken", "APP-SECRET"));
		s.put("pushover", Map.of("appToken", "APP-SECRET", "userKey", "USER-SECRET"));
		s.put("twilio", Map.of("sid", "AC1", "authToken", "AUTH-SECRET", "from", "+15550000", "to", "+15551111"));
		s.put("signal", Map.of("host", "127.0.0.1:8080", "from", "+15550000", "to", "+15551111"));
		s.put("whatsapp", Map.of("token", "WA-SECRET", "phoneId", "PHID", "to", "+15551111", "version", "v22.0"));
		s.put("zulip", Map.of("botEmail", "bot@x.com", "apiKey", "KEY-SECRET", "host", "myorg.zulipchat.com", "stream",
				"general", "topic", "deploys"));
		s.put("matrix", Map.of("token", "MX-SECRET", "host", "matrix.org", "roomId", "!room:matrix.org"));
		s.put("mastodon", Map.of("token", "MA-SECRET", "host", "mastodon.social"));
		s.put("bluesky",
				Map.of("identifier", "alice.bsky.social", "appPassword", "app-pass-1234", "host", "bsky.example"));
		s.put("pagerduty", Map.of("routingKey", "RK-SECRET"));
		s.put("opsgenie", Map.of("apiKey", "OG-SECRET"));
		s.put("pushbullet", Map.of("accessToken", "PB-SECRET"));
		return s;
	}

	@Test
	void everyParserSchemeHasADescriptor() {
		// 21 URL schemes (email is not a URL channel)
		assertThat(catalog.catalog()).hasSize(21);
		assertThat(samples().keySet())
			.containsExactlyInAnyOrderElementsOf(catalog.catalog().stream().map(ChannelDescriptor::scheme).toList());
	}

	@Test
	void buildUrlOutputParses_andParseRoundTripsFieldsWithSecretsMasked() {
		samples().forEach((scheme, values) -> {
			String url = catalog.buildUrl(scheme, values);

			// catalog <-> parser agreement: the built URL is accepted by the live parser
			assertThatCode(() -> parser.parse(url)).as("parser accepts %s url: %s", scheme, url)
				.doesNotThrowAnyException();

			// parse round-trips: non-secret fields verbatim, secret fields masked
			ParsedChannel parsed = catalog.parse(url);
			assertThat(parsed.scheme()).isEqualTo(scheme);
			ChannelDescriptor d = catalog.describe(scheme).orElseThrow();
			for (ChannelField f : d.fields()) {
				if (!values.containsKey(f.key())) {
					continue;
				}
				String expected = f.secret() ? ChannelCatalog.MASKED_SECRET : values.get(f.key());
				assertThat(parsed.values().get(f.key())).as("%s.%s", scheme, f.key()).isEqualTo(expected);
			}
		});
	}

	@Test
	void credentialBearingMatchesSecretFields() {
		for (ChannelDescriptor d : catalog.catalog()) {
			boolean anySecret = d.fields().stream().anyMatch(ChannelField::secret);
			assertThat(d.credentialBearing()).as("%s credentialBearing", d.scheme()).isEqualTo(anySecret);
		}
		// ntfy and signal are the only non-secret channels
		assertThat(catalog.describe("ntfy").orElseThrow().credentialBearing()).isFalse();
		assertThat(catalog.describe("signal").orElseThrow().credentialBearing()).isFalse();
		assertThat(catalog.describe("slack").orElseThrow().credentialBearing()).isTrue();
	}

	@Test
	void tagsAndCleartextHttpAreCarried() {
		String url = catalog.buildUrl("slack", Map.of("url", "example.com/x"), Set.of("failed"), true);
		assertThat(url).contains("slack+http://").contains("tags=failed");
		assertThatCode(() -> parser.parse(url)).doesNotThrowAnyException();

		ParsedChannel parsed = catalog.parse(url);
		assertThat(parsed.cleartextHttp()).isTrue();
		assertThat(parsed.tags()).containsExactly("failed");
	}

	@Test
	void whatsappVersionIsOptionalAndElidedWhenDefault() {
		// equal to the default -> elided
		assertThat(catalog.buildUrl("whatsapp",
				Map.of("token", "t", "phoneId", "p", "to", "+1", "version", WhatsAppNotifier.API_VERSION)))
			.doesNotContain("version");
		// absent -> elided
		assertThat(catalog.buildUrl("whatsapp", Map.of("token", "t", "phoneId", "p", "to", "+1")))
			.doesNotContain("version");
		// overridden -> present
		assertThat(catalog.buildUrl("whatsapp", Map.of("token", "t", "phoneId", "p", "to", "+1", "version", "v99.0")))
			.contains("version=v99.0");
	}

	@Test
	void validateReportsMissingRequiredFieldsByKey_withoutLeakingValues() {
		List<ChannelValidationError> errors = catalog.validate("twilio", Map.of("sid", "AC1"));
		assertThat(errors).extracting(ChannelValidationError::fieldKey)
			.contains("authToken", "from", "to")
			.doesNotContain("sid");
		assertThat(errors).allSatisfy((e) -> assertThat(e.message()).doesNotContain("AC1"));
		assertThat(catalog.validate("twilio", Map.of("sid", "AC1", "authToken", "x", "from", "+1", "to", "+2")))
			.isEmpty();
	}

	@Test
	void validateUnknownScheme() {
		assertThat(catalog.validate("bogus", Map.of())).singleElement()
			.satisfies((e) -> assertThat(e.code()).isEqualTo("unknown_scheme"));
	}

	@Test
	void describeUnknownIsEmpty_buildUrlUnknownThrows() {
		assertThat(catalog.describe("bogus")).isEmpty();
		assertThatThrownBy(() -> catalog.buildUrl("bogus", Map.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void blueskyDefaultHostIsElidedAndOptional() {
		// no host -> default bsky.social, omitted from the URL
		assertThat(catalog.buildUrl("bluesky", Map.of("identifier", "alice", "appPassword", "pw")))
			.isEqualTo("bluesky://alice:pw");
		// explicit default host -> still elided (canonical form)
		assertThat(
				catalog.buildUrl("bluesky", Map.of("identifier", "alice", "appPassword", "pw", "host", "bsky.social")))
			.isEqualTo("bluesky://alice:pw");

		ParsedChannel p = catalog.parse("bluesky://alice:pw");
		assertThat(p.values()).containsEntry("identifier", "alice").doesNotContainKey("host");
		assertThat(p.values().get("appPassword")).isEqualTo(ChannelCatalog.MASKED_SECRET);
	}

	@Test
	void redactMasksSecrets() {
		assertThat(catalog.redact("telegram://api.telegram.org/BOT-SECRET/42")).doesNotContain("BOT-SECRET");
	}

	@Test
	void parseRejectsBlankAndSchemelessUrls() {
		assertThatThrownBy(() -> catalog.parse("  ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> catalog.parse("no-scheme-here")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> catalog.parse("bogus://x")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parseWhatsappWithoutVersionOmitsTheField() {
		ParsedChannel parsed = catalog.parse("whatsapp://WA-SECRET@PHID/+15551111");
		assertThat(parsed.values()).containsEntry("phoneId", "PHID").doesNotContainKey("version");
		assertThat(parsed.values().get("token")).isEqualTo(ChannelCatalog.MASKED_SECRET);
	}

	@Test
	void edgeAndDegenerateInputsAreHandledDefensively() {
		// credential-only with an explicit host (userInfo branch) + +https explicit
		// transport
		assertThat(catalog.parse("pagerduty://key@events.pagerduty.com").values().get("routingKey"))
			.isEqualTo(ChannelCatalog.MASKED_SECRET);
		assertThat(catalog.parse("slack+https://h/x").cleartextHttp()).isFalse();

		// query with kept non-tags params + tags extracted; version found
		ParsedChannel wa = catalog.parse("whatsapp://t@PHID/+1?version=v9&foo=bar&tags=ops");
		assertThat(wa.values()).containsEntry("version", "v9");
		assertThat(wa.tags()).containsExactly("ops");
		// query present but version absent (queryParam not-found path)
		assertThat(catalog.parse("whatsapp://t@PHID/+1?foo=bar").values()).doesNotContainKey("version");

		// degenerate forms (missing delimiters) must not throw — defensive fallbacks
		for (String u : List.of("twilio://x", "signal://x", "zulip://x", "matrix://x", "mastodon://x", "pushover://x",
				"telegram://h")) {
			assertThatCode(() -> catalog.parse(u)).as(u).doesNotThrowAnyException();
		}

		// buildUrl: null values, null tags, null/unknown scheme
		assertThat(catalog.buildUrl("slack", null)).isEqualTo("slack://");
		assertThat(catalog.buildUrl("slack", Map.of("url", "h/x"), null, false)).isEqualTo("slack://h/x");
		assertThatThrownBy(() -> catalog.buildUrl(null, Map.of())).isInstanceOf(IllegalArgumentException.class);

		// describe/validate null + blank-value branch
		assertThat(catalog.describe(null)).isEmpty();
		assertThat(catalog.validate(null, Map.of())).singleElement()
			.satisfies((e) -> assertThat(e.code()).isEqualTo("unknown_scheme"));
		assertThat(catalog.validate("ntfy", Map.of("host", "h", "topic", " ")))
			.extracting(ChannelValidationError::fieldKey)
			.contains("topic");
	}

	@Test
	void descriptorsAndFieldsCarryDisplayMetadata() {
		ChannelDescriptor slack = catalog.describe("slack").orElseThrow();
		assertThat(slack.displayName()).isEqualTo("Slack");
		assertThat(slack.docsUrl()).startsWith("https://");

		for (ChannelDescriptor d : catalog.catalog()) {
			assertThat(d.displayName()).as("%s displayName", d.scheme()).isNotBlank();
			for (ChannelField f : d.fields()) {
				assertThat(f.label()).as("%s.%s label", d.scheme(), f.key()).isNotBlank();
				assertThat(f.description()).as("%s.%s description", d.scheme(), f.key()).isNotBlank();
				if (f.secret()) {
					// a secret field never advertises an example value
					assertThat(f.example()).as("%s.%s example", d.scheme(), f.key()).isNull();
				}
			}
		}
		// the generic webhook has no provider docs link; a non-secret field has an
		// example
		assertThat(catalog.describe("webhook").orElseThrow().docsUrl()).isNull();
		ChannelField chatId = slack.fields().stream().findFirst().orElseThrow(); // url
																					// (secret)
																					// ->
																					// no
																					// example
		assertThat(chatId.example()).isNull();
		assertThat(catalog.describe("telegram")
			.orElseThrow()
			.fields()
			.stream()
			.filter((f) -> f.key().equals("chatId"))
			.findFirst()
			.orElseThrow()
			.example()).isNotBlank();
	}

	@Test
	void buildUrlNormalisesPastedTransportOnUrlFields() {
		// https:// pasted verbatim from the provider is stripped, and still round-trips
		String https = catalog.buildUrl("slack", Map.of("url", "https://hooks.slack.com/services/T/B/xyz"));
		assertThat(https).isEqualTo("slack://hooks.slack.com/services/T/B/xyz");
		assertThatCode(() -> parser.parse(https)).doesNotThrowAnyException();
		assertThat(catalog.parse(https).cleartextHttp()).isFalse();

		// http:// flips to the documented +http (cleartext) transport
		String http = catalog.buildUrl("webhook", Map.of("url", "http://internal.example/hook"));
		assertThat(http).isEqualTo("webhook+http://internal.example/hook");
		assertThat(catalog.parse(http).cleartextHttp()).isTrue();

		// case-insensitive prefix; a bare host is left untouched
		assertThat(catalog.buildUrl("slack", Map.of("url", "HTTPS://h/x"))).isEqualTo("slack://h/x");
		assertThat(catalog.buildUrl("slack", Map.of("url", "h/x"))).isEqualTo("slack://h/x");
	}

	@Test
	void recomposePreservesUntouchedSecretsAndCarriesTagsAndTransport() {
		String original = catalog.buildUrl("telegram",
				Map.of("host", "api.telegram.org", "token", "BOT-SECRET", "chatId", "42"));
		ParsedChannel forEdit = catalog.parse(original);
		assertThat(forEdit.values().get("token")).isEqualTo(ChannelCatalog.MASKED_SECRET);

		// operator changes only chatId, leaves the masked token untouched -> secret kept
		Map<String, String> edited = new java.util.LinkedHashMap<>(forEdit.values());
		edited.put("chatId", "99");
		assertThat(catalog.recompose(original, edited)).isEqualTo("telegram://api.telegram.org/BOT-SECRET/99");

		// operator explicitly replaces the secret -> new value used
		edited.put("token", "NEW-BOT");
		assertThat(catalog.recompose(original, edited)).isEqualTo("telegram://api.telegram.org/NEW-BOT/99");

		// null edits -> everything preserved verbatim
		assertThat(catalog.recompose(original, null)).isEqualTo(original);

		// tags + cleartext transport carried across an edit of a (secret) webhook url
		String tagged = catalog.buildUrl("slack", Map.of("url", "h/x"), Set.of("ops"), true);
		assertThat(catalog.recompose(tagged, catalog.parse(tagged).values())).isEqualTo("slack+http://h/x?tags=ops");

		// an optional field absent in both prior and edits is simply omitted
		String bsky = catalog.buildUrl("bluesky", Map.of("identifier", "alice", "appPassword", "pw"));
		assertThat(catalog.recompose(bsky, null)).isEqualTo("bluesky://alice:pw");

		assertThatThrownBy(() -> catalog.recompose("  ", Map.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void buildUrlFromParsedChannelGuardsMaskedSecretsButRebuildsWhenReplaced() {
		String original = catalog.buildUrl("pagerduty", Map.of("routingKey", "REAL-KEY"));
		ParsedChannel masked = catalog.parse(original);
		// the #78 footgun is guarded: a still-masked ParsedChannel throws, not corrupts
		assertThatThrownBy(() -> catalog.buildUrl(masked)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("routingKey");

		// with the secret replaced, the symmetric overload round-trips
		ParsedChannel replaced = new ParsedChannel(masked.scheme(), Map.of("routingKey", "REAL-KEY"), masked.tags(),
				masked.cleartextHttp());
		assertThat(catalog.buildUrl(replaced)).isEqualTo(original);

		// a non-secret channel round-trips straight from parse()
		String ntfy = catalog.buildUrl("ntfy", Map.of("host", "ntfy.sh", "topic", "alerts"));
		assertThat(catalog.buildUrl(catalog.parse(ntfy))).isEqualTo(ntfy);

		assertThatThrownBy(() -> catalog.buildUrl((ParsedChannel) null)).isInstanceOf(IllegalArgumentException.class);
	}

	private static NotificationAdapter<String> adapter() {
		return new NotificationAdapter<>() {
			@Override
			public Object id(String e) {
				return e;
			}

			@Override
			public String status(String e) {
				return e;
			}

			@Override
			public String message(String e) {
				return e;
			}
		};
	}

}
