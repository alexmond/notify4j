package org.alexmond.notify4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base notifier: an {@code enabled} flag, a {@link #shouldNotify} guard, and error
 * isolation so a channel failure is logged and swallowed (never propagated to the
 * caller). Subclasses implement {@link #doNotify}. Delivery outcomes are recorded against
 * a {@link NotificationMetrics} sink (default no-op).
 *
 * @param <E> the application's event type
 */
public abstract class AbstractEventNotifier<E> implements Notifier<E> {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	private boolean enabled = true;

	private NotificationMetrics metrics = NotificationMetrics.NOOP;

	@Override
	public final void notify(E event) {
		if (!enabled) {
			return;
		}
		if (!shouldNotify(event)) {
			metrics.recordSuppressed(channelName());
			return;
		}
		try {
			doNotify(event);
			metrics.recordSent(channelName());
		}
		catch (RuntimeException ex) {
			metrics.recordFailed(channelName());
			log.warn("notifier {} failed for event {}: {}", channelName(), event, ex.getMessage());
		}
	}

	/** Whether this event should be delivered (default: always). */
	protected boolean shouldNotify(E event) {
		return true;
	}

	/** Deliver the notification. May throw; the wrapper swallows and logs. */
	protected abstract void doNotify(E event);

	/** Name used to tag metrics; the simple class name by default. */
	protected String channelName() {
		return getClass().getSimpleName();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Set the metrics sink for delivery outcomes; defaults to
	 * {@link NotificationMetrics#NOOP}.
	 */
	public void setMetrics(NotificationMetrics metrics) {
		this.metrics = (metrics != null) ? metrics : NotificationMetrics.NOOP;
	}

}
