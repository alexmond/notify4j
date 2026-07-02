package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link AbstractHttpNotifier#redact} must strip credentials (token in path/query, opaque
 * webhook URLs) so they never reach logs.
 */
class RedactionTest {

	@Test
	void stripsCredentialsKeepingSchemeAndHost() {
		// Telegram bot token lives in the path
		assertThat(AbstractHttpNotifier.redact("https://api.telegram.org/bot123:SECRET/sendMessage"))
			.isEqualTo("https://api.telegram.org/…");
		// Gotify token in the query
		assertThat(AbstractHttpNotifier.redact("https://gotify.example/message?token=SECRET"))
			.isEqualTo("https://gotify.example/…");
		// the whole Slack webhook URL is the secret
		assertThat(AbstractHttpNotifier.redact("https://hooks.slack.com/services/T0/B0/XXXXSECRET"))
			.isEqualTo("https://hooks.slack.com/…");
		// host:port preserved, path masked
		assertThat(AbstractHttpNotifier.redact("http://127.0.0.1:8080/hook")).isEqualTo("http://127.0.0.1:8080/…");
	}

	@Test
	void masksAuthorityForCredentialInAuthoritySchemes() {
		// the secret sits in the authority (parses as the URI host) — must not be
		// reflected
		assertThat(AbstractHttpNotifier.redact("pagerduty://R0123456789ABCDEF")).isEqualTo("pagerduty://…");
		assertThat(AbstractHttpNotifier.redact("opsgenie://api-key-uuid")).isEqualTo("opsgenie://…");
		assertThat(AbstractHttpNotifier.redact("pushbullet://o.TOKEN")).isEqualTo("pushbullet://…");
		assertThat(AbstractHttpNotifier.redact("pushover://APPTOKEN/USERKEY")).isEqualTo("pushover://…");
		// the +http transport signal is kept, but the secret is still masked
		assertThat(AbstractHttpNotifier.redact("pagerduty+http://R0123456789ABCDEF")).isEqualTo("pagerduty+http://…");
		// a credential scheme whose host is actually public is over-masked (safe for a
		// redactor)
		assertThat(AbstractHttpNotifier.redact("telegram://api.telegram.org/BOT-SECRET/42")).isEqualTo("telegram://…");
	}

	@Test
	void handlesNullAndMalformed() {
		assertThat(AbstractHttpNotifier.redact(null)).isEqualTo("<none>");
		assertThat(AbstractHttpNotifier.redact("not a url")).isEqualTo("<redacted>");
	}

}
