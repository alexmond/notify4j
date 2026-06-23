package org.alexmond.notify4j;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Builds {@link Notifications} facades from a URL list, applying the same adapter and
 * defaults ({@code ignore-changes}, log sink) every time. The single-tenant starter uses
 * it to build one app-wide facade; a multi-tenant application uses the <em>same</em>
 * factory to build one facade per tenant from that tenant's resolved channel URLs — so
 * per-tenant routing stays an application-layer concern while channel assembly + defaults
 * live in one place. Spring-free.
 *
 * @param <E> the application's event type
 */
public class NotificationsFactory<E> {

	private final NotificationAdapter<E> adapter;

	private final List<String> ignoreChanges;

	private final boolean includeLog;

	private final HttpClientConfig httpConfig;

	private final Executor executor;

	private final NotificationMetrics metrics;

	public NotificationsFactory(NotificationAdapter<E> adapter, List<String> ignoreChanges, boolean includeLog) {
		this(adapter, ignoreChanges, includeLog, HttpClientConfig.defaults(), null);
	}

	public NotificationsFactory(NotificationAdapter<E> adapter, List<String> ignoreChanges, boolean includeLog,
			HttpClientConfig httpConfig) {
		this(adapter, ignoreChanges, includeLog, httpConfig, null);
	}

	/**
	 * As above, with an optional {@link Executor} for asynchronous, non-blocking delivery
	 * ({@code null} = synchronous). Every facade this factory builds inherits it.
	 */
	public NotificationsFactory(NotificationAdapter<E> adapter, List<String> ignoreChanges, boolean includeLog,
			HttpClientConfig httpConfig, Executor executor) {
		this(adapter, ignoreChanges, includeLog, httpConfig, executor, null);
	}

	/**
	 * As above, with an optional {@link NotificationMetrics} sink ({@code null} = no
	 * metrics) applied to every facade this factory builds.
	 */
	public NotificationsFactory(NotificationAdapter<E> adapter, List<String> ignoreChanges, boolean includeLog,
			HttpClientConfig httpConfig, Executor executor, NotificationMetrics metrics) {
		this.adapter = adapter;
		this.ignoreChanges = ignoreChanges;
		this.includeLog = includeLog;
		this.httpConfig = httpConfig;
		this.executor = executor;
		this.metrics = (metrics != null) ? metrics : NotificationMetrics.NOOP;
	}

	/** Build a facade for the given channel URLs (no extra programmatic notifiers). */
	public Notifications<E> create(List<String> urls) {
		return create(urls, List.of());
	}

	/**
	 * Build a facade for the given channel URLs plus extra programmatic notifiers (e.g. a
	 * log sink, app beans).
	 */
	public Notifications<E> create(List<String> urls, List<? extends Notifier<E>> extraNotifiers) {
		Notifications<E> notifications = new Notifications<>(urls, adapter, extraNotifiers, ignoreChanges, includeLog,
				httpConfig, executor);
		notifications.setMetrics(metrics);
		return notifications;
	}

	public NotificationAdapter<E> adapter() {
		return adapter;
	}

}
