package org.alexmond.notify4j;

/**
 * A mute rule: suppresses matching events, optionally until it expires. Used by
 * {@link FilteringNotifier}.
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public interface NotificationFilter<E> {

	/** True to suppress (mute) this event. */
	boolean mutes(E event);

	/** True once this filter has expired and should be discarded. */
	default boolean isExpired() {
		return false;
	}

	/** Stable id so a mute can be looked up / removed. */
	String id();

}
