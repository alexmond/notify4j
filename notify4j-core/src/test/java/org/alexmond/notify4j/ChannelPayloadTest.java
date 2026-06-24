package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", (exchange) -> {
			path.set(exchange.getRequestURI().getPath());
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
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
				List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/message");
		assertThat(body.get()).isEqualTo("{\"title\":\"FAILED\",\"message\":\"build broke\"}");
	}

	@Test
	void pushoverPostsFormEncodedToMessagesEndpoint() {
		new PushoverNotifier<Evt>(base(), "tok", "usr", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/1/messages.json");
		assertThat(body.get()).isEqualTo("token=tok&user=usr&title=FAILED&message=build+broke");
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
				List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/");
		assertThat(body.get()).isEqualTo("{\"topic\":\"alerts\",\"title\":\"FAILED\",\"message\":\"build broke\"}");
	}

	@Test
	void pagerDutyEnqueuesTriggerEnvelope() {
		new PagerDutyNotifier<Evt>(base(), "rk-1", HttpClientConfig.defaults(), Evt::id, Evt::status, Evt::message,
				List.of())
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
				List.of())
			.notify(evt());
		assertThat(path.get()).isEqualTo("/v2/alerts");
		assertThat(auth.get()).isEqualTo("GenieKey key-9");
		assertThat(body.get()).contains("\"message\":\"build broke\"")
			.contains("\"alias\":7")
			.contains("\"status\":\"FAILED\"");
	}

	record Evt(long id, String status, String message) {
	}

}
