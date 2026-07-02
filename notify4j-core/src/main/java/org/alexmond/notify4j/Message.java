package org.alexmond.notify4j;

import java.util.Objects;

/**
 * A ready-to-send notification for the <em>one-shot</em> path — a title, a body, and an
 * optional {@link Severity}. Pass one to {@link Notifications#sendOnce} to deliver an
 * ad-hoc message to a set of channels without defining an event type or a
 * {@link NotificationAdapter} (a pipeline "notify step", an "alert now" caller).
 *
 * <p>
 * Unlike the streaming path, a {@code Message} carries no identity or status: a one-shot
 * has no transition to track, so every {@code sendOnce} fires unconditionally. This is
 * caller <em>input</em> — construct it directly (via the canonical constructor or the
 * {@code of(…)} factories), unlike the read-only descriptor records the catalog returns.
 *
 * @param title a short subject for channels that have one (email, ntfy, Pushover,
 * Gotify); may be {@code null} for none
 * @param body the message body; must not be {@code null}
 * @param severity the severity mapped onto channels with a native priority notion; never
 * {@code null} (defaults to {@link Severity#DEFAULT})
 * @since 1.1.0
 */
public record Message(String title, String body, Severity severity) {

	/**
	 * Canonical constructor: {@code body} is required; a {@code null} {@code severity}
	 * becomes {@link Severity#DEFAULT}.
	 */
	public Message {
		Objects.requireNonNull(body, "body");
		if (severity == null) {
			severity = Severity.DEFAULT;
		}
	}

	/** A body-only message: no title, {@link Severity#DEFAULT}. */
	public static Message of(String body) {
		return new Message(null, body, Severity.DEFAULT);
	}

	/** A titled message with {@link Severity#DEFAULT}. */
	public static Message of(String title, String body) {
		return new Message(title, body, Severity.DEFAULT);
	}

	/** A titled message with an explicit severity. */
	public static Message of(String title, String body, Severity severity) {
		return new Message(title, body, severity);
	}

}
