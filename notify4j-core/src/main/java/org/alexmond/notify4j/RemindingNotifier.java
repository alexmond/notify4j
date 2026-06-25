package org.alexmond.notify4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorates a delegate and re-notifies for entities stuck in a "reminder" status (e.g. an
 * app that stays DOWN) on an interval until they resolve. Best for <em>persistent</em>
 * states; not for one-shot events. Reusable library wrapper.
 *
 * <p>
 * {@link #checkReminders(Instant)} is the testable core; {@link #start()}/{@link #stop()}
 * schedule it on a daemon thread.
 */
public class RemindingNotifier<E> implements Notifier<E>, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(RemindingNotifier.class);

	private final Notifier<E> delegate;

	private final Function<E, Object> idFn;

	private final Function<E, String> statusFn;

	private final Set<String> reminderStatuses;

	private final Duration reminderPeriod;

	private final Map<Object, Reminder<E>> reminders = new ConcurrentHashMap<>();

	private final ReentrantLock schedulerLock = new ReentrantLock();

	private Duration checkInterval = Duration.ofMinutes(1);

	private ScheduledExecutorService scheduler;

	/**
	 * @param delegate the notifier to (re-)deliver through
	 * @param idFn reads the entity id from an event (the reminder key)
	 * @param statusFn reads the current status from an event
	 * @param reminderStatuses statuses that arm a reminder (e.g. {@code DOWN}); any other
	 * status clears it
	 * @param reminderPeriod how long an entity must stay in a reminder status before it
	 * is re-notified
	 */
	public RemindingNotifier(Notifier<E> delegate, Function<E, Object> idFn, Function<E, String> statusFn,
			Set<String> reminderStatuses, Duration reminderPeriod) {
		this.delegate = delegate;
		this.idFn = idFn;
		this.statusFn = statusFn;
		this.reminderStatuses = Set.copyOf(reminderStatuses);
		this.reminderPeriod = reminderPeriod;
	}

	@Override
	public void notify(E event) {
		delegate.notify(event);
		Object id = idFn.apply(event);
		if (reminderStatuses.contains(statusFn.apply(event))) {
			reminders.put(id, new Reminder<>(event, Instant.now()));
		}
		else {
			reminders.remove(id);
		}
	}

	/** Re-fire any reminder whose period has elapsed by {@code now}. */
	public void checkReminders(Instant now) {
		reminders.forEach((id, r) -> {
			if (!r.lastNotified().plus(reminderPeriod).isAfter(now)) {
				delegate.notify(r.event());
				reminders.put(id, new Reminder<>(r.event(), now));
			}
		});
	}

	/**
	 * Start the daemon scheduler that runs {@link #checkReminders} every check interval.
	 * Idempotent.
	 */
	public void start() {
		schedulerLock.lock();
		try {
			if (scheduler != null) {
				return;
			}
			scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
				Thread t = new Thread(r, "notify-reminder");
				t.setDaemon(true);
				return t;
			});
			long ms = checkInterval.toMillis();
			scheduler.scheduleWithFixedDelay(() -> {
				try {
					checkReminders(Instant.now());
				}
				catch (RuntimeException ex) {
					log.warn("reminder check failed: {}", ex.getMessage());
				}
			}, ms, ms, TimeUnit.MILLISECONDS);
		}
		finally {
			schedulerLock.unlock();
		}
	}

	/**
	 * Equivalent to {@link #stop()} — lets {@link Notifications#close()} stop the
	 * scheduler.
	 */
	@Override
	public void close() {
		stop();
	}

	/** Stop the scheduler (if running). Idempotent; safe to call from a shutdown hook. */
	public void stop() {
		schedulerLock.lock();
		try {
			if (scheduler != null) {
				scheduler.shutdownNow();
				scheduler = null;
			}
		}
		finally {
			schedulerLock.unlock();
		}
	}

	public void setCheckInterval(Duration checkInterval) {
		this.checkInterval = checkInterval;
	}

	public int reminderCount() {
		return reminders.size();
	}

	private record Reminder<E>(E event, Instant lastNotified) {
	}

}
