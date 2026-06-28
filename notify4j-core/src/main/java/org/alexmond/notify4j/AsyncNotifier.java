package org.alexmond.notify4j;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorates a notifier to deliver on an {@link Executor} instead of the caller's thread,
 * so a slow or hung channel never blocks the caller or its sibling channels. The
 * submitted task isolates failures (logged, never propagated), preserving the
 * {@link Notifier} contract. {@link Notifications} wraps each channel in one of these
 * when an executor is configured.
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public class AsyncNotifier<E> implements Notifier<E> {

	private static final Logger log = LoggerFactory.getLogger(AsyncNotifier.class);

	private final Notifier<E> delegate;

	private final Executor executor;

	private final NotificationMetrics metrics;

	private final String channelName;

	public AsyncNotifier(Notifier<E> delegate, Executor executor) {
		this(delegate, executor, NotificationMetrics.NOOP, delegate.getClass().getSimpleName());
	}

	/**
	 * @param delegate the channel to deliver to off-thread
	 * @param executor the pool to deliver on
	 * @param metrics sink that records a {@link NotificationMetrics#recordDropped drop}
	 * when the pool rejects delivery (queue full); may be {@code null} for no-op
	 * @param channelName name used to tag the drop metric
	 */
	public AsyncNotifier(Notifier<E> delegate, Executor executor, NotificationMetrics metrics, String channelName) {
		this.delegate = delegate;
		this.executor = executor;
		this.metrics = (metrics != null) ? metrics : NotificationMetrics.NOOP;
		this.channelName = channelName;
	}

	@Override
	public void notify(E event) {
		try {
			executor.execute(() -> {
				try {
					delegate.notify(event);
				}
				catch (RuntimeException ex) {
					log.warn("async notifier {} failed: {}", this.channelName, ex.getMessage());
				}
			});
		}
		catch (RejectedExecutionException ex) {
			// Pool saturated: drop rather than block the caller or grow the queue
			// unbounded. Recorded so back-pressure is observable, not silent.
			this.metrics.recordDropped(this.channelName);
			log.warn("async delivery dropped for {} (delivery queue full): {}", this.channelName, ex.getMessage());
		}
	}

}
