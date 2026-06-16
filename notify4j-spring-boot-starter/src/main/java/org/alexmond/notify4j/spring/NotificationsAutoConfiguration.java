package org.alexmond.notify4j.spring;

import java.util.List;
import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.Notifications;
import org.alexmond.notify4j.NotificationsFactory;
import org.alexmond.notify4j.Notifier;
import org.springframework.beans.factory.ObjectProvider;
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
 */
@AutoConfiguration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationsAutoConfiguration {

	/**
	 * Raw types here are deliberate: the facade is generic over the app's event type,
	 * which is only known to the {@link NotificationAdapter} bean. Spring injects that
	 * single adapter and all {@link Notifier} beans by raw type, and the facade fans out
	 * to them uniformly.
	 */
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
	public NotificationsFactory notificationsFactory(NotificationProperties props, NotificationAdapter adapter) {
		return new NotificationsFactory<>(adapter, props.getIgnoreChanges(), props.isLog());
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
					adapter::id, adapter::status, adapter::message, props.getIgnoreChanges());
		}

	}

}
