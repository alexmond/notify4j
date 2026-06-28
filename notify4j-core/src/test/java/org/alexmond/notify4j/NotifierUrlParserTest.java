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
		assertThat(notifier("mattermost://my-host/hooks/k")).isInstanceOf(MattermostNotifier.class);
		assertThat(notifier("rocketchat://my-host/hooks/i/t")).isInstanceOf(RocketChatNotifier.class);
		assertThat(notifier("googlechat://chat.googleapis.com/v1/spaces/s/messages"))
			.isInstanceOf(GoogleChatNotifier.class);
		assertThat(notifier("gotify://my-host/app-token")).isInstanceOf(GotifyNotifier.class);
		assertThat(notifier("pushover://app-token/user-key")).isInstanceOf(PushoverNotifier.class);
		assertThat(notifier("twilio://AC1:tok@+15550000/+15551111")).isInstanceOf(TwilioNotifier.class);
		assertThat(notifier("signal+http://127.0.0.1:8080/+15550000/+15551111")).isInstanceOf(SignalNotifier.class);
		assertThat(notifier("whatsapp://token@phone-id/+15551111")).isInstanceOf(WhatsAppNotifier.class);
		assertThat(notifier("zulip://bot@x.com:key@myorg.zulipchat.com/general/deploys"))
			.isInstanceOf(ZulipNotifier.class);
		assertThat(notifier("pushbullet://o.access-token")).isInstanceOf(PushbulletNotifier.class);
	}

	@Test
	void handParsedSchemesStripStrayQuery() {
		// a stray non-tags query must not land inside the last credential/target segment
		assertThat(notifier("pushover://tok/user?x=1")).isInstanceOf(PushoverNotifier.class);
		assertThat(notifier("twilio://AC:tok@+15550000/+15551111?x=1")).isInstanceOf(TwilioNotifier.class);
	}

	@Test
	void zulipParsesBotEmailContainingAtSign() {
		// the bot email itself has an '@', so the host split must use the LAST '@'
		assertThat(notifier("zulip://bot@x.com:key@myorg.zulipchat.com/general/deploys"))
			.isInstanceOf(ZulipNotifier.class);
		assertThatThrownBy(() -> parser.parse("zulip://bot@x.com:key@host/only-stream"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topic");
	}

	@Test
	void pushoverRequiresTokenAndUser() {
		assertThatThrownBy(() -> parser.parse("pushover://only-token")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("user-key");
	}

	@Test
	void messagingChannelsValidateTheirParts() {
		assertThatThrownBy(() -> parser.parse("twilio://AC1:tok@+15550000"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("from");
		assertThatThrownBy(() -> parser.parse("signal://host/only-from")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("from");
		assertThatThrownBy(() -> parser.parse("whatsapp://token@phone-id")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("phone-id");
	}

	@Test
	void googleChatKeepsKeyAndTokenQueryParams() {
		// non-tags query params survive; ?tags= is stripped
		NotifierUrlParser.Channel<Event> ch = parser
			.parse("googlechat://chat.googleapis.com/v1/spaces/s/messages?key=K&token=T&tags=ops");
		assertThat(ch.tags()).containsExactly("ops");
		assertThat(ch.notifier()).isInstanceOf(GoogleChatNotifier.class);
	}

	@Test
	void gotifyRequiresAnAppToken() {
		assertThatThrownBy(() -> parser.parse("gotify://my-host/")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("app-token");
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

	@Test
	void parseErrorsDoNotLeakSecrets() {
		// A bad URL throws at startup; the message must not carry the token/key/sid into
		// the boot log. Each of these is malformed and credential-bearing in a different
		// position (path, query, user-info, authority).
		record Bad(String url, String secret) {
		}
		List<Bad> cases = List.of(new Bad("telegram://api.telegram.org/SECRET-BOT-TOKEN", "SECRET-BOT-TOKEN"),
				new Bad("pushover://SECRET-APP-TOKEN", "SECRET-APP-TOKEN"),
				new Bad("twilio://SID:SECRET-AUTH-TOKEN@from", "SECRET-AUTH-TOKEN"),
				new Bad("whatsapp://SECRET-GRAPH-TOKEN@phone-id", "SECRET-GRAPH-TOKEN"),
				new Bad("zulip://bot@x.com:SECRET-API-KEY@host", "SECRET-API-KEY"),
				new Bad("opsgenie+https://", "anything"));
		for (Bad b : cases) {
			assertThatThrownBy(() -> parser.parse(b.url())).isInstanceOf(IllegalArgumentException.class)
				.satisfies((ex) -> assertThat(ex.getMessage()).doesNotContain(b.secret()));
		}
	}

	private Notifier<Event> notifier(String url) {
		return parser.parse(url).notifier();
	}

	/** A trivial event whose three fields are read directly. */
	private record Event(Object id, String status, String message) {
	}

}
