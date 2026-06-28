package org.alexmond.notify4j.spring;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import org.alexmond.notify4j.HttpClientConfig;
import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.NotificationMetrics;
import org.alexmond.notify4j.Notifications;
import org.alexmond.notify4j.NotificationsConfig;
import org.alexmond.notify4j.NotificationsFactory;
import org.alexmond.notify4j.Notifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Auto-configures a {@link Notifications} facade from {@code notify4j.urls} once the
 * application supplies a {@link NotificationAdapter} bean (which teaches the channels how
 * to read the app's event type). Any other {@link Notifier} beans on the context are
 * folded in as extra fan-out targets.
 *
 * <p>
 * The raw types in these bean methods are deliberate: the facade is generic over the
 * app's event type, which is only known to the {@link NotificationAdapter} bean. Spring
 * injects that single adapter and all {@link Notifier} beans by raw type, and the facade
 * fans out to them uniformly.
 *
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationsAutoConfiguration {

	/**
	 * The factory that assembles {@link Notifications} from a URL list with the
	 * configured defaults. Always available once an adapter is present — a multi-tenant
	 * app injects this to build a facade per tenant (and typically sets
	 * {@code notify4j.global=false} to suppress the single global one).
	 */
	@Bean
	@ConditionalOnBean(NotificationAdapter.class)
	@ConditionalOnMissingBean
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public NotificationsFactory notificationsFactory(NotificationProperties props, NotificationAdapter adapter,
			@Qualifier("notify4jAsyncExecutor") ObjectProvider<Executor> asyncExecutor,
			ObjectProvider<NotificationMetrics> metrics) {
		NotificationProperties.Http http = props.getHttp();
		HttpClientConfig httpConfig = HttpClientConfig.of(http.getConnectTimeout(), http.getReadTimeout(),
				http.getMaxAttempts(), http.getRetryBackoff());
		NotificationsConfig config = NotificationsConfig.builder()
			.ignoreChanges(props.getIgnoreChanges())
			.includeLog(props.isLog())
			.http(httpConfig)
			.executor(asyncExecutor.getIfAvailable())
			.metrics(metrics.getIfAvailable())
			.build();
		return new NotificationsFactory<>(adapter, config);
	}

	/**
	 * Shared daemon thread pool for asynchronous, non-blocking delivery. Present (and so
	 * delivery is async) unless {@code notify4j.async.enabled=false}. A
	 * {@link ThreadPoolTaskExecutor} so Spring shuts it down gracefully on context close
	 * (waits for in-flight deliveries, then interrupts).
	 */
	@Bean(name = "notify4jAsyncExecutor")
	@ConditionalOnBean(NotificationAdapter.class)
	@ConditionalOnProperty(name = "notify4j.async.enabled", matchIfMissing = true)
	public Executor notify4jAsyncExecutor(NotificationProperties props) {
		NotificationProperties.Async async = props.getAsync();
		int poolSize = Math.max(1, async.getPoolSize());
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(poolSize);
		executor.setMaxPoolSize(poolSize);
		// Bound the queue so a burst or a stuck channel can't grow it unbounded and OOM
		// the
		// host. With pool and queue both full, the rejection policy decides: DROP (abort
		// →
		// AsyncNotifier records the drop and logs) or CALLER_RUNS (back-pressure).
		executor.setQueueCapacity(Math.max(0, async.getQueueCapacity()));
		executor.setRejectedExecutionHandler(
				(async.getRejectionPolicy() == NotificationProperties.RejectionPolicy.CALLER_RUNS)
						? new ThreadPoolExecutor.CallerRunsPolicy() : new ThreadPoolExecutor.AbortPolicy());
		executor.setThreadNamePrefix("notify4j-async-");
		executor.setDaemon(true);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(5);
		return executor;
	}

	/**
	 * The single, app-wide facade (single-tenant default). Built from
	 * {@code notify4j.urls} via the factory, folding in any other {@link Notifier} beans.
	 * Disable with {@code notify4j.global=false} for multi-tenant apps that build
	 * per-tenant facades from the factory instead.
	 */
	@Bean
	@ConditionalOnBean(NotificationsFactory.class)
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "notify4j.global", matchIfMissing = true)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Notifications notifications(NotificationProperties props, NotificationsFactory factory,
			ObjectProvider<Notifier> extraNotifiers) {
		List<Notifier> extras = extraNotifiers.orderedStream().toList();
		return factory.create(props.getUrls(), extras);
	}

	/**
	 * Email is the one channel that isn't a URL: it's Spring-mail-coupled and SMTP comes
	 * from {@code spring.mail.*}. Registered as an extra {@link Notifier} bean (so the
	 * facade folds it in) when a {@link JavaMailSender} is configured and
	 * {@code notify4j.email.to} is set.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(JavaMailSender.class)
	static class EmailConfiguration {

		@Bean
		@ConditionalOnBean({ NotificationAdapter.class, JavaMailSender.class })
		@ConditionalOnProperty(prefix = "notify4j.email", name = "to")
		@SuppressWarnings({ "rawtypes", "unchecked" })
		Notifier emailNotifier(NotificationProperties props, NotificationAdapter adapter, JavaMailSender mailSender) {
			NotificationProperties.Email email = props.getEmail();
			return new EmailNotifier<>(mailSender, email.getFrom(), email.getTo(), email.getSubjectPrefix(),
					adapter::id, adapter::status, adapter::message, adapter::title, props.getIgnoreChanges());
		}

	}

	/**
	 * Per-channel delivery metrics (sent/failed/suppressed), recorded to Micrometer.
	 * Active only when {@code micrometer-core} is on the classpath and a
	 * {@link MeterRegistry} bean exists (e.g. with Spring Boot Actuator). The factory
	 * folds it into every facade.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(MeterRegistry.class)
	static class MetricsConfiguration {

		@Bean
		@ConditionalOnBean({ NotificationAdapter.class, MeterRegistry.class })
		@ConditionalOnMissingBean
		NotificationMetrics notify4jMetrics(MeterRegistry registry) {
			return new MicrometerNotificationMetrics(registry);
		}

	}

}
