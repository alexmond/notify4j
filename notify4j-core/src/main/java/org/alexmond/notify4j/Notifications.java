package org.alexmond.notify4j;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <p>
 * For a single ad-hoc notification with no event type or adapter (a pipeline "notify
 * step", an "alert now" caller), use the static {@link #sendOnce(List, Message)}.
 * </p>
 *
 * @param <E> the application's event type
 * @since 1.0.0
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

	/** Pool for asynchronous delivery, or {@code null} for synchronous on the caller. */
	private final Executor executor;

	/**
	 * Re-notifier for entities stuck in a reminder status, or {@code null} when disabled.
	 */
	private final RemindingNotifier<E> reminder;

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
		this.executor = config.executor();
		if (config.includeLog()) {
			register(new LoggingNotifier<>(), Set.of());
		}
		if (extraNotifiers != null) {
			for (Notifier<E> n : extraNotifiers) {
				register(n, Set.of());
			}
		}
		if (urls != null) {
			NotifierUrlParser<E> parser = new NotifierUrlParser<>(adapter, config.ignoreChanges(), config.http());
			for (String url : urls) {
				if (url != null && !url.isBlank()) {
					Channel<E> channel = parser.parse(url.trim());
					register(channel.notifier(), channel.tags());
				}
			}
		}
		this.reminder = buildReminder(adapter, config);
		log.info("notifications: {} channel(s) configured ({} delivery{})", channels.size(),
				(this.executor != null) ? "async" : "sync", (this.reminder != null) ? ", reminders on" : "");
	}

	// --- one-shot imperative send --------------------------------------------

	/**
	 * Deliver a one-off {@link Message} to {@code urls} and return the per-channel tally
	 * — no event type, adapter, or {@code close()} required. Construct, send, and close
	 * all happen in this one call. Delivery is <strong>synchronous</strong> (it returns
	 * only after every channel has been attempted, so a short-lived process won't exit
	 * mid-send), the message always fires (a one-shot has no transition to suppress), and
	 * a failing channel is counted, never thrown. Uses
	 * {@link HttpClientConfig#defaults()} (10s timeouts, no retry).
	 * @param urls channel URLs (see {@link NotifierUrlParser});
	 * <strong>secret-bearing</strong> — never log them
	 * @param message the message to send
	 * @return the delivery tally ({@code attempted == sent + failed})
	 * @since 1.1.0
	 */
	public static SendResult sendOnce(List<String> urls, Message message) {
		return sendOnce(urls, message, HttpClientConfig.defaults());
	}

	/**
	 * {@link #sendOnce(List, Message)} with tuned HTTP timeouts/retry. Delivery is forced
	 * synchronous regardless of {@code http}'s retry mode — a one-shot must complete
	 * before it returns — so a config built for asynchronous use is coerced to blocking
	 * retry.
	 * @param urls channel URLs; <strong>secret-bearing</strong> — never log them
	 * @param message the message to send
	 * @param http HTTP timeouts + retry policy (coerced to blocking)
	 * @return the delivery tally
	 * @since 1.1.0
	 */
	public static SendResult sendOnce(List<String> urls, Message message, HttpClientConfig http) {
		Objects.requireNonNull(message, "message");
		HttpClientConfig h = (http != null) ? http : HttpClientConfig.defaults();
		if (h.nonBlockingRetry()) {
			h = new HttpClientConfig(h.client(), h.requestTimeout(), h.maxAttempts(), h.retryBackoff(), false);
		}
		CountingMetrics counter = new CountingMetrics();
		NotificationsConfig config = NotificationsConfig.builder().includeLog(false).http(h).metrics(counter).build();
		try (Notifications<Message> notifications = new Notifications<>(urls, MessageAdapter.INSTANCE, config)) {
			notifications.send(message);
		}
		return counter.result();
	}

	/**
	 * Build and start the reminder when configured, or return {@code null}. The reminder
	 * re-fires a stuck entity to every channel; it first
	 * {@link AbstractEventNotifier#forgetTransition forgets} that entity's transition
	 * state so the re-send isn't suppressed by a channel's transition filter as a
	 * non-change.
	 */
	private RemindingNotifier<E> buildReminder(NotificationAdapter<E> adapter, NotificationsConfig config) {
		if (config.reminderStatuses().isEmpty()) {
			return null;
		}
		java.util.function.Function<E, Object> idFn = adapter::id;
		Notifier<E> reFire = (event) -> {
			if (filtering.isMuted(event)) {
				return;
			}
			Object id = idFn.apply(event);
			for (Notifier<E> sink : this.sinks) {
				try {
					if (sink instanceof AbstractEventNotifier<?> aen) {
						@SuppressWarnings("unchecked")
						AbstractEventNotifier<E> typed = (AbstractEventNotifier<E>) aen;
						typed.forgetTransition(id);
					}
					sink.notify(event);
				}
				catch (RuntimeException ex) {
					log.warn("reminder via {} failed: {}", sink.getClass().getSimpleName(), ex.getMessage());
				}
			}
		};
		RemindingNotifier<E> r = new RemindingNotifier<>(reFire, idFn, adapter::status, config.reminderStatuses(),
				config.reminderPeriod());
		r.setCheckInterval(config.reminderCheckInterval());
		r.start();
		return r;
	}

	/**
	 * Apply metrics to the raw notifier, track it for {@link #close()}, and add the
	 * channel — wrapped in an {@link AsyncNotifier} (carrying the metrics + channel name
	 * so a queue-full drop is recorded) when an executor is configured.
	 */
	private void register(Notifier<E> raw, Set<String> tags) {
		String name = raw.getClass().getSimpleName();
		if (raw instanceof AbstractEventNotifier<?> aen) {
			aen.setMetrics(this.metrics);
			name = aen.channelName();
		}
		this.sinks.add(raw);
		Notifier<E> sink = (this.executor != null) ? new AsyncNotifier<>(raw, this.executor, this.metrics, name) : raw;
		this.channels.add(new Channel<>(sink, tags));
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
		deliverToChannels(event, routeTags);
		if (reminder != null) {
			// Track (arm/clear) the entity for reminders without re-delivering it.
			reminder.observe(event);
		}
	}

	private void deliverToChannels(E event, Collection<String> routeTags) {
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
		if (this.reminder != null) {
			this.reminder.stop();
		}
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

	/**
	 * The built-in adapter for the {@link #sendOnce one-shot} path. Maps a
	 * {@link Message} onto the channel fields; {@code id} is {@code null} so every
	 * message is delivered unconditionally — a null id bypasses each channel's transition
	 * filter and stores no state, so even a repeated send is never suppressed.
	 * Package-private on purpose: callers reach it only through {@code sendOnce}.
	 */
	static final class MessageAdapter implements NotificationAdapter<Message> {

		static final MessageAdapter INSTANCE = new MessageAdapter();

		@Override
		public Object id(Message event) {
			return null; // no identity: a one-shot always fires
		}

		@Override
		public String status(Message event) {
			return "ALERT";
		}

		@Override
		public String message(Message event) {
			return event.body();
		}

		@Override
		public String title(Message event) {
			return event.title();
		}

		@Override
		public Severity severity(Message event) {
			return event.severity();
		}

	}

	/** Tallies per-channel delivery outcomes for a synchronous {@link #sendOnce}. */
	private static final class CountingMetrics implements NotificationMetrics {

		private final AtomicInteger sent = new AtomicInteger();

		private final AtomicInteger failed = new AtomicInteger();

		@Override
		public void recordSent(String channel) {
			this.sent.incrementAndGet();
		}

		@Override
		public void recordFailed(String channel) {
			this.failed.incrementAndGet();
		}

		@Override
		public void recordSuppressed(String channel) {
			// a one-shot (null id) is never suppressed
		}

		SendResult result() {
			int s = this.sent.get();
			int f = this.failed.get();
			return new SendResult(s + f, s, f);
		}

	}

}
