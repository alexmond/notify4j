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
		s.put("pagerduty", Map.of("routingKey", "RK-SECRET"));
		s.put("opsgenie", Map.of("apiKey", "OG-SECRET"));
		s.put("pushbullet", Map.of("accessToken", "PB-SECRET"));
		return s;
	}

	@Test
	void everyParserSchemeHasADescriptor() {
		// 20 URL schemes (email is not a URL channel)
		assertThat(catalog.catalog()).hasSize(20);
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
	void redactMasksSecrets() {
		assertThat(catalog.redact("telegram://api.telegram.org/BOT-SECRET/42")).doesNotContain("BOT-SECRET");
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
