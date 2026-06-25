/**
 * Spring Boot auto-configuration for notify4j, bound from {@code notify4j.*}
 * ({@link org.alexmond.notify4j.spring.NotificationProperties}).
 *
 * <p>
 * {@link org.alexmond.notify4j.spring.NotificationsAutoConfiguration} builds a
 * {@link org.alexmond.notify4j.NotificationsFactory} and an app-wide
 * {@link org.alexmond.notify4j.Notifications} facade once the application supplies a
 * {@link org.alexmond.notify4j.NotificationAdapter} bean, folding in any other
 * {@code Notifier} beans. It also wires the one channel that is not a URL —
 * {@link org.alexmond.notify4j.spring.EmailNotifier} (SMTP via {@code spring.mail.*}) —
 * and, when a Micrometer {@code MeterRegistry} is present, per-channel metrics
 * ({@link org.alexmond.notify4j.spring.MicrometerNotificationMetrics}).
 */
package org.alexmond.notify4j.spring;
