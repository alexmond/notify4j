package org.alexmond.notify4j;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
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
 * {@code notify4j.urls}. Behaviour is configured by an immutable
 * {@link NotificationsConfig}.
 * </p>
 *
 * <p>
 * {@link AutoCloseable}: {@link #close()} closes any channel that holds resources (e.g. a
 * {@link RemindingNotifier}); the config's {@link NotificationsConfig#executor()
 * executor} is caller-owned and is not shut down by the facade.
 * </p>
 *
 * @param <E> the application's event type
 */
public class Notifications<E> implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(Notifications.class);

	private final List<Channel<E>> channels = new CopyOnWriteArrayList<>();

	private final FilteringNotifier<E> filtering = new FilteringNotifier<>((event) -> {
	});

	/**
	 * The raw (pre-async-wrap) notifiers, so metrics can be applied to the real channels.
	 */
	private final List<Notifier<E>> sinks = new CopyOnWriteArrayList<>();

	private NotificationMetrics metrics = NotificationMetrics.NOOP;

	/**
	 * Facade for {@code urls} with all defaults ({@link NotificationsConfig#defaults()}).
	 */
	public Notifications(List<String> urls, NotificationAdapter<E> adapter) {
		this(urls, adapter, List.of(), NotificationsConfig.defaults());
	}

	/** Facade for {@code urls} with the given config. */
	public Notifications(List<String> urls, NotificationAdapter<E> adapter, NotificationsConfig config) {
		this(urls, adapter, List.of(), config);
	}

	/**
	 * @param urls channel URLs (see {@link NotifierUrlParser}); {@code null}/blank
	 * entries skipped
	 * @param adapter reads id/status/message from the event
	 * @param extraNotifiers additional programmatic notifiers to fan out to (untagged;
	 * may be {@code null})
	 * @param config delivery settings — ignore-changes, log sink, HTTP, executor, metrics
	 */
	public Notifications(List<String> urls, NotificationAdapter<E> adapter, List<? extends Notifier<E>> extraNotifiers,
			NotificationsConfig config) {
		this.metrics = config.metrics();
		Executor executor = config.executor();
		UnaryOperator<Notifier<E>> wrap = (executor != null) ? (n) -> new AsyncNotifier<>(n, executor) : (n) -> n;
		if (config.includeLog()) {
			register(new LoggingNotifier<>(), Set.of(), wrap);
		}
		if (extraNotifiers != null) {
			for (Notifier<E> n : extraNotifiers) {
				register(n, Set.of(), wrap);
			}
		}
		if (urls != null) {
			NotifierUrlParser<E> parser = new NotifierUrlParser<>(adapter, config.ignoreChanges(), config.http());
			for (String url : urls) {
				if (url != null && !url.isBlank()) {
					Channel<E> channel = parser.parse(url.trim());
					register(channel.notifier(), channel.tags(), wrap);
				}
			}
		}
		log.info("notifications: {} channel(s) configured ({} delivery)", channels.size(),
				(executor != null) ? "async" : "sync");
	}

	/**
	 * Apply metrics to the raw notifier, track it for {@link #close()}, and add the
	 * (wrapped) channel.
	 */
	private void register(Notifier<E> raw, Set<String> tags, UnaryOperator<Notifier<E>> wrap) {
		if (raw instanceof AbstractEventNotifier<?> aen) {
			aen.setMetrics(this.metrics);
		}
		this.sinks.add(raw);
		this.channels.add(new Channel<>(wrap.apply(raw), tags));
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

	/**
	 * Register an additional untagged notifier at runtime. Unlike the channels built at
	 * construction, a notifier added here is <em>not</em> wrapped for asynchronous
	 * delivery, so it runs on the {@link #send} caller's thread; wrap it in an
	 * {@link AsyncNotifier} first if that matters.
	 */
	public void addNotifier(Notifier<E> notifier) {
		sinks.add(notifier);
		if (notifier instanceof AbstractEventNotifier<?> aen) {
			aen.setMetrics(metrics);
		}
		channels.add(new Channel<>(notifier, Set.of()));
	}

	/**
	 * Close any channel that holds resources (anything {@link AutoCloseable}, e.g. a
	 * {@link RemindingNotifier}). The configured {@link NotificationsConfig#executor()
	 * executor} is caller-owned and is <em>not</em> shut down here.
	 */
	@Override
	@SuppressWarnings("PMD.CloseResource") // closing the channels is precisely this
											// method's job
	public void close() {
		for (Notifier<E> sink : this.sinks) {
			if (sink instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				}
				catch (Exception ex) {
					log.warn("closing notifier {} failed: {}", sink.getClass().getSimpleName(), ex.getMessage());
				}
			}
		}
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
