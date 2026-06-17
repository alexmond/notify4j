package org.alexmond.notify4j;

import java.util.List;

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

	public NotificationsFactory(NotificationAdapter<E> adapter, List<String> ignoreChanges, boolean includeLog) {
		this(adapter, ignoreChanges, includeLog, HttpClientConfig.defaults());
	}

	public NotificationsFactory(NotificationAdapter<E> adapter, List<String> ignoreChanges, boolean includeLog,
			HttpClientConfig httpConfig) {
		this.adapter = adapter;
		this.ignoreChanges = ignoreChanges;
		this.includeLog = includeLog;
		this.httpConfig = httpConfig;
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
		return new Notifications<>(urls, adapter, extraNotifiers, ignoreChanges, includeLog, httpConfig);
	}

	public NotificationAdapter<E> adapter() {
		return adapter;
	}

}
