/**
 * notify4j core — an embeddable, domain-agnostic multi-channel notification engine. No
 * Spring dependency; channels use the JDK {@link java.net.http.HttpClient}.
 *
 * <h2>Key seams</h2>
 * <ul>
 * <li>{@link org.alexmond.notify4j.Notifier} — the SPI ({@code void notify(E)}).
 * Implementations must never throw; a failing channel must not break the caller.</li>
 * <li>{@link org.alexmond.notify4j.NotificationAdapter} — supplied by the application;
 * maps its event type {@code E} to an {@code id}, {@code status}, and {@code message}.
 * The only place the app's domain type is referenced, which keeps channels
 * event-agnostic.</li>
 * <li>{@link org.alexmond.notify4j.Notifications} — the application-facing facade
 * (fan-out + runtime mute + tag routing).</li>
 * <li>{@link org.alexmond.notify4j.NotificationsFactory} — builds facades from a URL list
 * with shared defaults (one global facade, or one per tenant).</li>
 * <li>{@link org.alexmond.notify4j.NotifierUrlParser} — turns an Apprise/shoutrrr-style
 * URL into a channel; its class Javadoc lists every supported scheme.</li>
 * </ul>
 *
 * <h2>Cross-cutting</h2> Channels gate on meaningful status transitions
 * ({@link org.alexmond.notify4j.TransitionFilter}), can be wrapped for asynchronous
 * delivery ({@link org.alexmond.notify4j.AsyncNotifier}) and retry transient HTTP
 * failures ({@link org.alexmond.notify4j.HttpClientConfig}), and record per-channel
 * delivery outcomes against the dependency-free
 * {@link org.alexmond.notify4j.NotificationMetrics} SPI.
 *
 * <h2>Public API &amp; stability (since 1.0.0)</h2> This package is the stable public
 * API: the SPIs ({@code Notifier}, {@code NotificationAdapter},
 * {@code NotificationFilter}, {@code NotificationMetrics}), the facade
 * ({@code Notifications}, {@code NotificationsFactory}, {@code NotificationsConfig}), the
 * value types ({@code Severity}, {@code HttpClientConfig}), the decorators
 * ({@code AsyncNotifier}, {@code CompositeNotifier}, {@code FilteringNotifier},
 * {@code RemindingNotifier}, {@code LoggingNotifier}), the {@code Abstract*} extension
 * points, and the URL grammar of {@code NotifierUrlParser}. These follow semantic
 * versioning within 1.x.
 *
 * <p>
 * The {@link org.alexmond.notify4j.internal} package is <strong>not</strong> public API:
 * the concrete per-channel notifier classes live there and are constructed only via
 * channel URLs (see {@code NotifierUrlParser}); their classes and constructors may change
 * in any release. Configure channels through URLs and the facade, not by instantiating
 * them.
 */
package org.alexmond.notify4j;
