package org.alexmond.notify4j;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * A {@link NotificationFilter} backed by a predicate, optionally expiring at a given
 * instant.
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public class ExpiringFilter<E> implements NotificationFilter<E> {

	private final String id;

	private final Predicate<E> predicate;

	private final Instant expiresAt;

	private final Clock clock;

	/**
	 * @param id stable id so the mute can be looked up / removed
	 * @param predicate true to suppress (mute) a given event
	 * @param expiresAt when to drop this filter; {@code null} = never expires
	 */
	public ExpiringFilter(String id, Predicate<E> predicate, Instant expiresAt) {
		this(id, predicate, expiresAt, Clock.systemUTC());
	}

	/**
	 * As {@link #ExpiringFilter(String, Predicate, Instant)} but with an explicit
	 * {@link Clock} for {@link #isExpired()} — mainly so expiry can be tested
	 * deterministically.
	 * @param id stable id so the mute can be looked up / removed
	 * @param predicate true to suppress (mute) a given event
	 * @param expiresAt when to drop this filter; {@code null} = never expires
	 * @param clock the clock {@link #isExpired()} reads "now" from
	 * @since 1.1.0
	 */
	public ExpiringFilter(String id, Predicate<E> predicate, Instant expiresAt, Clock clock) {
		this.id = id;
		this.predicate = predicate;
		this.expiresAt = expiresAt;
		this.clock = clock;
	}

	@Override
	public boolean mutes(E event) {
		return predicate.test(event);
	}

	@Override
	public boolean isExpired() {
		return expiresAt != null && clock.instant().isAfter(expiresAt);
	}

	@Override
	public String id() {
		return id;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

}
