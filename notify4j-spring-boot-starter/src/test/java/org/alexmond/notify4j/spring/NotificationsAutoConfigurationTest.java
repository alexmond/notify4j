package org.alexmond.notify4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;

import java.util.List;
import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.Notifications;
import org.alexmond.notify4j.NotificationsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The facade is auto-configured only when an adapter is present, and binds
 * {@code notify4j.urls}.
 */
class NotificationsAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(NotificationsAutoConfiguration.class));

	@Test
	void noAdapterMeansNoFacade() {
		runner.run((ctx) -> assertThat(ctx).doesNotHaveBean(Notifications.class));
	}

	@Test
	void adapterTriggersFacadeAndUrlsBecomeChannels() {
		runner.withUserConfiguration(AdapterConfig.class)
			.withPropertyValues("notify4j.urls[0]=slack://hooks.slack.com/services/A/B/C",
					"notify4j.urls[1]=webhook://example.com/notify")
			.run((ctx) -> {
				assertThat(ctx).hasSingleBean(Notifications.class);
				// log sink + 2 channels
				assertThat(ctx.getBean(Notifications.class).channelCount()).isEqualTo(3);
			});
	}

	@Test
	void globalFacadeCanBeDisabledWhileTheFactoryRemains() {
		// multi-tenant apps suppress the single global facade and build per-tenant ones
		// from the factory
		runner.withUserConfiguration(AdapterConfig.class).withPropertyValues("notify4j.global=false").run((ctx) -> {
			assertThat(ctx).doesNotHaveBean(Notifications.class);
			assertThat(ctx).hasSingleBean(NotificationsFactory.class);
		});
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void factoryBuildsAFacadeOnDemand() {
		runner.withUserConfiguration(AdapterConfig.class).run((ctx) -> {
			assertThat(ctx).hasSingleBean(NotificationsFactory.class);
			NotificationsFactory factory = ctx.getBean(NotificationsFactory.class);
			Notifications built = factory.create(List.of("webhook://example.com/notify"));
			assertThat(built.channelCount()).isEqualTo(2); // log sink + the one channel
		});
	}

	@Test
	void logSinkCanBeDisabled() {
		runner.withUserConfiguration(AdapterConfig.class)
			.withPropertyValues("notify4j.log=false")
			.run((ctx) -> assertThat(ctx.getBean(Notifications.class).channelCount()).isZero());
	}

	@Test
	void emailChannelWiresInWhenMailSenderAndRecipientPresent() {
		runner.withUserConfiguration(AdapterConfig.class, MailConfig.class)
			.withPropertyValues("notify4j.email.to=dev@team.local")
			.run((ctx) -> assertThat(ctx.getBean(Notifications.class).channelCount()).isEqualTo(2)); // log
																										// +
																										// email
	}

	@Test
	void emailChannelStaysOffWithoutRecipient() {
		runner.withUserConfiguration(AdapterConfig.class, MailConfig.class)
			.run((ctx) -> assertThat(ctx.getBean(Notifications.class).channelCount()).isEqualTo(1)); // log
																										// only
	}

	@Configuration
	static class MailConfig {

		@Bean
		JavaMailSender mailSender() {
			return mock(JavaMailSender.class);
		}

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
