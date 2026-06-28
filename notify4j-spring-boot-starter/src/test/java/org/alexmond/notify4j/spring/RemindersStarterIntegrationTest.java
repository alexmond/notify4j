package org.alexmond.notify4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.Notifications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code notify4j.reminders.*} properties wire a working reminder onto the
 * auto-configured facade: a stuck entity is re-notified to the channel until it resolves.
 */
class RemindersStarterIntegrationTest {

	private HttpServer server;

	private final CountDownLatch hits = new CountDownLatch(2); // initial send + ≥1
																// reminder

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/hook", (exchange) -> {
			this.hits.countDown();
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void remindersPropertiesReNotifyAStuckEntity() {
		int port = server.getAddress().getPort();
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NotificationsAutoConfiguration.class))
			.withUserConfiguration(AdapterConfig.class)
			.withPropertyValues("notify4j.urls[0]=webhook+http://127.0.0.1:" + port + "/hook", "notify4j.log=false",
					"notify4j.reminders.enabled=true", "notify4j.reminders.statuses[0]=FAILED",
					"notify4j.reminders.period=1ms", "notify4j.reminders.check-interval=20ms")
			.run((ctx) -> {
				Notifications facade = ctx.getBean(Notifications.class);
				facade.send("FAILED");
				// the channel is hit once for the send and again for the reminder(s)
				assertThat(this.hits.await(5, TimeUnit.SECONDS)).isTrue();
			});
	}

	@Configuration
	static class AdapterConfig {

		@Bean
		NotificationAdapter<String> adapter() {
			return new NotificationAdapter<>() {
				@Override
				public Object id(String e) {
					return "svc";
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

}
