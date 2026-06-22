package org.alexmond.notify4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * End-to-end check that the auto-configured facade delivers asynchronously through the
 * configured HTTP pipeline (shared pool + retry policy), exercising the
 * {@code notify4j.*} property binding and the async executor.
 */
class AsyncDeliveryIntegrationTest {

	private HttpServer server;

	private final CountDownLatch delivered = new CountDownLatch(1);

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/hook", (exchange) -> {
			this.delivered.countDown();
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
	void asyncFacadeDeliversThroughConfiguredPipeline() {
		int port = server.getAddress().getPort();
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NotificationsAutoConfiguration.class))
			.withUserConfiguration(AdapterConfig.class)
			.withPropertyValues("notify4j.urls[0]=webhook+http://127.0.0.1:" + port + "/hook",
					"notify4j.async.pool-size=2", "notify4j.http.max-attempts=2", "notify4j.http.retry-backoff=1ms",
					"notify4j.http.connect-timeout=2s", "notify4j.http.read-timeout=2s", "notify4j.email.from=ci@x",
					"notify4j.email.subject-prefix=[t]")
			.run((ctx) -> {
				Notifications facade = ctx.getBean(Notifications.class);
				facade.send("svc-1:FAILED");
				assertThat(this.delivered.await(3, TimeUnit.SECONDS)).isTrue();
			});
	}

	@Configuration
	static class AdapterConfig {

		@Bean
		NotificationAdapter<String> adapter() {
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

}
