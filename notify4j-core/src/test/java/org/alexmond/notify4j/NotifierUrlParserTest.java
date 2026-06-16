package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The URL parser routes each scheme to the right channel and decodes the telegram
 * token/chat segments.
 */
class NotifierUrlParserTest {

	private static final NotificationAdapter<Event> ADAPTER = new NotificationAdapter<>() {
		@Override
		public Object id(Event e) {
			return e.id();
		}

		@Override
		public String status(Event e) {
			return e.status();
		}

		@Override
		public String message(Event e) {
			return e.message();
		}
	};

	private final NotifierUrlParser<Event> parser = new NotifierUrlParser<>(ADAPTER, List.of());

	@Test
	void routesEachSchemeToItsChannelType() {
		assertThat(notifier("slack://hooks.slack.com/services/A/B/C")).isInstanceOf(SlackNotifier.class);
		assertThat(notifier("teams://outlook.office.com/webhook/x")).isInstanceOf(TeamsNotifier.class);
		assertThat(notifier("discord://discord.com/api/webhooks/1/2")).isInstanceOf(DiscordNotifier.class);
		assertThat(notifier("webhook://example.com/notify")).isInstanceOf(WebhookNotifier.class);
		assertThat(notifier("telegram://api.telegram.org/token/42")).isInstanceOf(TelegramNotifier.class);
		assertThat(notifier("ntfy://ntfy.sh/my-topic")).isInstanceOf(NtfyNotifier.class);
		assertThat(notifier("pagerduty://routing-key-123")).isInstanceOf(PagerDutyNotifier.class);
		assertThat(notifier("opsgenie://api-key-abc")).isInstanceOf(OpsGenieNotifier.class);
	}

	@Test
	void escalationSchemesAcceptAnExplicitHostForTesting() {
		assertThat(notifier("pagerduty+http://rk@127.0.0.1:9000")).isInstanceOf(PagerDutyNotifier.class);
		assertThat(notifier("opsgenie+http://ak@127.0.0.1:9000")).isInstanceOf(OpsGenieNotifier.class);
	}

	@Test
	void ntfyRequiresATopic() {
		assertThatThrownBy(() -> parser.parse("ntfy://ntfy.sh/")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topic");
	}

	@Test
	void httpTransportSuffixIsAccepted() {
		assertThat(notifier("webhook+http://127.0.0.1:9000/sink")).isInstanceOf(WebhookNotifier.class);
	}

	@Test
	void telegramRequiresTokenAndChatId() {
		assertThatThrownBy(() -> parser.parse("telegram://api.telegram.org/only-token"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chat-id");
	}

	@Test
	void tagsQueryIsParsedAndStrippedFromTheEndpoint() {
		NotifierUrlParser.Channel<Event> ch = parser.parse("pagerduty://rk?tags=failed,oncall");
		assertThat(ch.tags()).containsExactlyInAnyOrder("failed", "oncall");
		assertThat(ch.matches(Set.of("failed"))).isTrue();
		assertThat(ch.matches(Set.of("success"))).isFalse();

		// an untagged channel matches any route
		assertThat(parser.parse("slack://h/s/A/B/C").matches(Set.of("anything"))).isTrue();
	}

	@Test
	void unknownSchemeAndBadTransportAreRejected() {
		assertThatThrownBy(() -> parser.parse("carrier-pigeon://nest/3")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unknown notification scheme");
		assertThatThrownBy(() -> parser.parse("slack+ftp://host/path")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unsupported transport");
		assertThatThrownBy(() -> parser.parse("no-scheme-here")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("missing scheme");
	}

	private Notifier<Event> notifier(String url) {
		return parser.parse(url).notifier();
	}

	/** A trivial event whose three fields are read directly. */
	private record Event(Object id, String status, String message) {
	}

}
