package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins down the exact wire shape of each channel's JSON body (and OpsGenie's auth
 * header), so a malformed Slack/Teams/Discord/Telegram/ntfy/PagerDuty/OpsGenie payload
 * cannot regress silently. Captures the request against a local {@link HttpServer}; the
 * endpoint is the captured path so we also assert the Telegram/PagerDuty/OpsGenie path
 * suffixes.
 */
class ChannelPayloadTest {

	private HttpServer server;

	private final AtomicReference<String> path = new AtomicReference<>();

	private final AtomicReference<String> body = new AtomicReference<>();

	private final AtomicReference<String> auth = new AtomicReference<>();

	private final AtomicReference<String> accessToken = new AtomicReference<>();

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", (exchange) -> {
			path.set(exchange.getRequestURI().getPath());
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			accessToken.set(exchange.getRequestHeaders().getFirst("Access-Token"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String base() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private static Evt evt() {
		return new Evt(7, "FAILED", "build broke");
	}

	@Test
	void slackPostsText() {
		new SlackNotifier<Evt>(base() + "/hook", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"text\":\"build broke\"}");
	}

	@Test
	void teamsPostsMessageCard() {
		new TeamsNotifier<Evt>(base() + "/hook", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"@type\":\"MessageCard\",\"@context\":\"https://schema.org/extensions\","
				+ "\"summary\":\"FAILED\",\"title\":\"FAILED\",\"text\":\"build broke\"}");
	}

	@Test
	void discordPostsContent() {
		new DiscordNotifier<Evt>(base() + "/hook", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"content\":\"build broke\"}");
	}

	@Test
	void mattermostPostsText() {
		new MattermostNotifier<Evt>(base() + "/hooks/k", HttpClientConfig.defaults(), Evt::id, Evt::status,
				Evt::message, List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"text\":\"build broke\"}");
	}

	@Test
	void rocketChatPostsText() {
		new RocketChatNotifier<Evt>(base() + "/hooks/i/t", HttpClientConfig.defaults(), Evt::id, Evt::status,
				Evt::message, List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"text\":\"build broke\"}");
	}

	@Test
	void googleChatPostsText() {
		new GoogleChatNotifier<Evt>(base() + "/v1/spaces/s/messages", HttpClientConfig.defaults(), Evt::id, Evt::status,
				Evt::message, List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"text\":\"build broke\"}");
	}

	@Test
	void gotifyPostsTitleAndMessageToMessageEndpoint() {
		new GotifyNotifier<Evt>(base(), "apptok", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> null, (e) -> Severity.DEFAULT, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/message");
		assertThat(body.get()).isEqualTo("{\"title\":\"FAILED\",\"message\":\"build broke\"}");
	}

	@Test
	void pushoverPostsFormEncodedToMessagesEndpoint() {
		new PushoverNotifier<Evt>(base(), "tok", "usr", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> null, (e) -> Severity.DEFAULT, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/1/messages.json");
		assertThat(body.get()).isEqualTo("token=tok&user=usr&title=FAILED&message=build+broke");
	}

	@Test
	void twilioPostsFormEncodedSmsWithBasicAuth() {
		new TwilioNotifier<Evt>(base(), "AC1", "tok", "+15550000", "+15551111", HttpClientConfig.defaults(), Evt::id,
				Evt::status, Evt::message, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/2010-04-01/Accounts/AC1/Messages.json");
		assertThat(auth.get())
			.isEqualTo("Basic " + Base64.getEncoder().encodeToString("AC1:tok".getBytes(StandardCharsets.UTF_8)));
		assertThat(body.get()).isEqualTo("To=%2B15551111&From=%2B15550000&Body=build+broke");
	}

	@Test
	void signalPostsJsonToBridge() {
		new SignalNotifier<Evt>(base(), "+15550000", "+15551111", HttpClientConfig.defaults(), Evt::id, Evt::status,
				Evt::message, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/v2/send");
		assertThat(body.get())
			.isEqualTo("{\"number\":\"+15550000\",\"recipients\":[\"+15551111\"],\"message\":\"build broke\"}");
	}

	@Test
	void whatsAppPostsCloudApiEnvelopeWithBearer() {
		new WhatsAppNotifier<Evt>(base(), "tok", "PHID", "+15551111", HttpClientConfig.defaults(), Evt::id, Evt::status,
				Evt::message, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/" + WhatsAppNotifier.API_VERSION + "/PHID/messages");
		assertThat(auth.get()).isEqualTo("Bearer tok");
		assertThat(body.get()).isEqualTo("{\"messaging_product\":\"whatsapp\",\"to\":\"+15551111\","
				+ "\"type\":\"text\",\"text\":{\"body\":\"build broke\"}}");
	}

	@Test
	void whatsAppHonoursExplicitApiVersion() {
		new WhatsAppNotifier<Evt>(base(), "tok", "PHID", "+15551111", "v22.0", HttpClientConfig.defaults(), Evt::id,
				Evt::status, Evt::message, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/v22.0/PHID/messages");
	}

	@Test
	void zulipPostsFormEncodedStreamMessageWithBasicAuth() {
		new ZulipNotifier<Evt>(base(), "bot@x.com", "key", "general", "deploys", HttpClientConfig.defaults(), Evt::id,
				Evt::status, Evt::message, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/api/v1/messages");
		assertThat(auth.get())
			.isEqualTo("Basic " + Base64.getEncoder().encodeToString("bot@x.com:key".getBytes(StandardCharsets.UTF_8)));
		assertThat(body.get()).isEqualTo("type=stream&to=general&topic=deploys&content=build+broke");
	}

	@Test
	void pushbulletPostsNoteWithAccessTokenHeader() {
		new PushbulletNotifier<Evt>(base(), "o.tok", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/v2/pushes");
		assertThat(accessToken.get()).isEqualTo("o.tok");
		assertThat(body.get()).isEqualTo("{\"type\":\"note\",\"title\":\"FAILED\",\"body\":\"build broke\"}");
	}

	@Test
	void telegramPostsChatIdAndTextToSendMessage() {
		new TelegramNotifier<Evt>(base(), "tok123", "chat456", HttpClientConfig.defaults(), Evt::id, Evt::status,
				Evt::message, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/bottok123/sendMessage");
		assertThat(body.get()).isEqualTo("{\"chat_id\":\"chat456\",\"text\":\"build broke\"}");
	}

	@Test
	void ntfyPostsTopicTitleAndMessageToRoot() {
		new NtfyNotifier<Evt>(base(), "alerts", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> null, (e) -> Severity.DEFAULT, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/");
		assertThat(body.get()).isEqualTo("{\"topic\":\"alerts\",\"title\":\"FAILED\",\"message\":\"build broke\"}");
	}

	@Test
	void pagerDutyEnqueuesTriggerEnvelope() {
		new PagerDutyNotifier<Evt>(base(), "rk-1", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> null, (e) -> Severity.DEFAULT, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/v2/enqueue");
		assertThat(body.get()).contains("\"routing_key\":\"rk-1\"")
			.contains("\"event_action\":\"trigger\"")
			.contains("\"dedup_key\":7")
			.contains("\"summary\":\"build broke\"")
			.contains("\"severity\":\"error\"")
			.contains("\"status\":\"FAILED\"")
			.doesNotContain("builder");
	}

	@Test
	void opsGenieCreatesAlertWithGenieKeyHeader() {
		new OpsGenieNotifier<Evt>(base(), "key-9", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> null, (e) -> Severity.DEFAULT, List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/v2/alerts");
		assertThat(auth.get()).isEqualTo("GenieKey key-9");
		assertThat(body.get()).contains("\"message\":\"build broke\"")
			.contains("\"alias\":7")
			.contains("\"status\":\"FAILED\"")
			.doesNotContain("\"priority\""); // DEFAULT severity omits priority
	}

	@Test
	void severityAndTitleEnrichChannelsThatSupportThem() {
		// PagerDuty maps severity onto its own scale (was hard-coded "error").
		new PagerDutyNotifier<Evt>(base(), "rk", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> "Build failed", (e) -> Severity.CRITICAL, List.of())
			.notify(evt());
		assertThat(body.get()).contains("\"severity\":\"critical\"");

		// OpsGenie adds a P-priority only when severity is set.
		new OpsGenieNotifier<Evt>(base(), "k", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> null, (e) -> Severity.WARNING, List.of())
			.notify(evt());
		assertThat(body.get()).contains("\"priority\":\"P3\"");

		// ntfy: title overrides the status, severity becomes a numeric priority.
		new NtfyNotifier<Evt>(base(), "alerts", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> "Deploy", (e) -> Severity.ERROR, List.of())
			.notify(evt());
		assertThat(body.get())
			.isEqualTo("{\"topic\":\"alerts\",\"title\":\"Deploy\",\"message\":\"build broke\",\"priority\":4}");

		// Gotify: title + numeric priority.
		new GotifyNotifier<Evt>(base(), "tok", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> "Deploy", (e) -> Severity.INFO, List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("{\"title\":\"Deploy\",\"message\":\"build broke\",\"priority\":1}");

		// Pushover: title + form-encoded priority.
		new PushoverNotifier<Evt>(base(), "tok", "usr", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				(e) -> "Deploy", (e) -> Severity.CRITICAL, List.of())
			.notify(evt());
		assertThat(body.get()).isEqualTo("token=tok&user=usr&title=Deploy&message=build+broke&priority=2");
	}

	record Evt(long id, String status, String message) {
	}

}
