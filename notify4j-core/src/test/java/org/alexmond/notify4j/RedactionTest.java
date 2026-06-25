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
	void handlesNullAndMalformed() {
		assertThat(AbstractHttpNotifier.redact(null)).isEqualTo("<none>");
		assertThat(AbstractHttpNotifier.redact("not a url")).isEqualTo("<redacted>");
	}

}
