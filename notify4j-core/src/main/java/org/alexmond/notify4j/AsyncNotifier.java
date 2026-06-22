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
 */
public class AsyncNotifier<E> implements Notifier<E> {

	private static final Logger log = LoggerFactory.getLogger(AsyncNotifier.class);

	private final Notifier<E> delegate;

	private final Executor executor;

	public AsyncNotifier(Notifier<E> delegate, Executor executor) {
		this.delegate = delegate;
		this.executor = executor;
	}

	@Override
	public void notify(E event) {
		try {
			executor.execute(() -> {
				try {
					delegate.notify(event);
				}
				catch (RuntimeException ex) {
					log.warn("async notifier {} failed: {}", delegate.getClass().getSimpleName(), ex.getMessage());
				}
			});
		}
		catch (RejectedExecutionException ex) {
			log.warn("async delivery rejected for {}: {}", delegate.getClass().getSimpleName(), ex.getMessage());
		}
	}

}
