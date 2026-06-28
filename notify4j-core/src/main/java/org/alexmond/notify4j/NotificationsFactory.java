package org.alexmond.notify4j;

import java.util.List;

/**
 * Builds {@link Notifications} facades from a URL list, applying the same adapter and
 * {@link NotificationsConfig} every time. The single-tenant starter uses it to build one
 * app-wide facade; a multi-tenant application uses the <em>same</em> factory to build one
 * facade per tenant from that tenant's resolved channel URLs — so per-tenant routing
 * stays an application-layer concern while channel assembly + config live in one place.
 * Spring-free.
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public class NotificationsFactory<E> {

	private final NotificationAdapter<E> adapter;

	private final NotificationsConfig config;

	/** Factory with all-default config ({@link NotificationsConfig#defaults()}). */
	public NotificationsFactory(NotificationAdapter<E> adapter) {
		this(adapter, NotificationsConfig.defaults());
	}

	public NotificationsFactory(NotificationAdapter<E> adapter, NotificationsConfig config) {
		this.adapter = adapter;
		this.config = config;
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
		return new Notifications<>(urls, this.adapter, extraNotifiers, this.config);
	}

	public NotificationAdapter<E> adapter() {
		return this.adapter;
	}

}
