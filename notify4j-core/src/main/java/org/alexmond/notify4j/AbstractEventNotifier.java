package org.alexmond.notify4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base notifier: an {@code enabled} flag, a {@link #shouldNotify} guard, and error
 * isolation so a channel failure is logged and swallowed (never propagated to the
 * caller). Subclasses implement {@link #doNotify}.
 */
public abstract class AbstractEventNotifier<E> implements Notifier<E> {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	private boolean enabled = true;

	@Override
	public final void notify(E event) {
		if (!enabled || !shouldNotify(event)) {
			return;
		}
		try {
			doNotify(event);
		}
		catch (RuntimeException ex) {
			log.warn("notifier {} failed for event {}: {}", getClass().getSimpleName(), event, ex.getMessage());
		}
	}

	/** Whether this event should be delivered (default: always). */
	protected boolean shouldNotify(E event) {
		return true;
	}

	/** Deliver the notification. May throw; the wrapper swallows and logs. */
	protected abstract void doNotify(E event);

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
