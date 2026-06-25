package org.alexmond.notify4j;

import java.time.Instant;
import java.util.function.Predicate;

/**
 * A {@link NotificationFilter} backed by a predicate, optionally expiring at a given
 * instant.
 *
 * @param <E> the application's event type
 */
public class ExpiringFilter<E> implements NotificationFilter<E> {

	private final String id;

	private final Predicate<E> predicate;

	private final Instant expiresAt;

	/**
	 * @param id stable id so the mute can be looked up / removed
	 * @param predicate true to suppress (mute) a given event
	 * @param expiresAt when to drop this filter; {@code null} = never expires
	 */
	public ExpiringFilter(String id, Predicate<E> predicate, Instant expiresAt) {
		this.id = id;
		this.predicate = predicate;
		this.expiresAt = expiresAt;
	}

	@Override
	public boolean mutes(E event) {
		return predicate.test(event);
	}

	@Override
	public boolean isExpired() {
		return expiresAt != null && Instant.now().isAfter(expiresAt);
	}

	@Override
	public String id() {
		return id;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

}
