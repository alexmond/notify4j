package org.alexmond.notify4j;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.alexmond.notify4j.NotifierUrlParser.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application-facing facade for the notification subsystem. Builds a fan-out of channel
 * notifiers from a list of {@link NotifierUrlParser Apprise-style URLs} (plus any extra
 * programmatic notifiers and an always-on log sink). Two gates sit in front of delivery:
 * a {@link FilteringNotifier} for runtime mutes, and per-channel <em>tags</em> so an
 * event can be routed to a subset of channels (e.g. failures → PagerDuty). Each channel
 * also applies its own {@link TransitionFilter} (fire on real status changes only).
 *
 * <p>
 * Spring-free: the {@code spring-boot-starter} wires one of these as a bean from
 * {@code notifications.urls}.
 * </p>
 *
 * @param <E> the application's event type
 */
public class Notifications<E> {

	private static final Logger log = LoggerFactory.getLogger(Notifications.class);

	private final List<Channel<E>> channels = new CopyOnWriteArrayList<>();

	private final FilteringNotifier<E> filtering = new FilteringNotifier<>((event) -> {
	});

	/**
	 * @param urls channel URLs (see {@link NotifierUrlParser}); {@code null}/blank
	 * entries skipped
	 * @param adapter reads id/status/message from the event
	 * @param extraNotifiers additional programmatic notifiers to fan out to (untagged;
	 * may be {@code null})
	 * @param ignoreChanges transition patterns to suppress (applied per channel)
	 * @param includeLog whether to add a {@link LoggingNotifier} sink (always-on default)
	 */
	public Notifications(List<String> urls, NotificationAdapter<E> adapter, List<? extends Notifier<E>> extraNotifiers,
			List<String> ignoreChanges, boolean includeLog) {
		this(urls, adapter, extraNotifiers, ignoreChanges, includeLog, HttpClientConfig.defaults());
	}

	/**
	 * As above, with explicit {@link HttpClientConfig} (shared HTTP client + timeouts)
	 * for the webhook-style channels.
	 */
	public Notifications(List<String> urls, NotificationAdapter<E> adapter, List<? extends Notifier<E>> extraNotifiers,
			List<String> ignoreChanges, boolean includeLog, HttpClientConfig httpConfig) {
		if (includeLog) {
			channels.add(new Channel<>(new LoggingNotifier<>(), Set.of()));
		}
		if (extraNotifiers != null) {
			for (Notifier<E> n : extraNotifiers) {
				channels.add(new Channel<>(n, Set.of()));
			}
		}
		if (urls != null) {
			NotifierUrlParser<E> parser = new NotifierUrlParser<>(adapter, ignoreChanges, httpConfig);
			for (String url : urls) {
				if (url != null && !url.isBlank()) {
					channels.add(parser.parse(url.trim()));
				}
			}
		}
		log.info("notifications: {} channel(s) configured", channels.size());
	}

	/**
	 * Deliver an event to every (untagged) channel, unless an active mute suppresses it.
	 */
	public void send(E event) {
		send(event, List.of());
	}

	/**
	 * Deliver an event, routing to channels whose tags overlap {@code routeTags}
	 * (untagged channels always fire), unless an active mute suppresses it.
	 */
	public void send(E event, Collection<String> routeTags) {
		if (filtering.isMuted(event)) {
			return;
		}
		for (Channel<E> channel : channels) {
			if (channel.matches(routeTags)) {
				try {
					channel.notifier().notify(event);
				}
				catch (RuntimeException ex) {
					log.warn("notifier {} failed: {}", channel.notifier().getClass().getSimpleName(), ex.getMessage());
				}
			}
		}
	}

	/** Register an additional untagged notifier at runtime. */
	public void addNotifier(Notifier<E> notifier) {
		channels.add(new Channel<>(notifier, Set.of()));
	}

	/** Add (or replace, by id) a runtime mute. */
	public void addFilter(NotificationFilter<E> filter) {
		filtering.addFilter(filter);
	}

	/** Remove a mute by id; returns true if one was removed. */
	public boolean removeFilter(String id) {
		return filtering.removeFilter(id);
	}

	/** Active (non-expired) mutes. */
	public List<NotificationFilter<E>> filters() {
		return filtering.getFilters();
	}

	/** Number of configured channels (including the log sink and any extras). */
	public int channelCount() {
		return channels.size();
	}

}
