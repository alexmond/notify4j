package org.alexmond.notify4j;

/**
 * Bridges an application's event type {@code E} to the three things every channel needs:
 * a stable {@code id} (for transition tracking), a {@code status} string, and a
 * human-readable {@code message}. Supplying one adapter per application lets the
 * URL-configured channels stay event-agnostic — the channels never reference the app's
 * domain type, only this adapter does.
 *
 * @param <E> the application's event type (e.g. a pipeline-run event)
 */
public interface NotificationAdapter<E> {

	/** Stable identity of the event's subject, used to detect status transitions. */
	Object id(E event);

	/** The current status of the subject (e.g. {@code SUCCESS}, {@code FAILED}). */
	String status(E event);

	/** A human-readable message for the notification body. */
	String message(E event);

	/**
	 * An optional short title/subject for the notification. Channels that have a title or
	 * subject field (email, ntfy, Pushover, Gotify) use it; the default is {@code null},
	 * meaning "no title" — those channels then fall back to their historical behaviour
	 * (e.g. the status as the title).
	 * @return the title, or {@code null} for none
	 * @since 1.0.0
	 */
	default String title(E event) {
		return null;
	}

	/**
	 * The optional severity of the notification. Channels with a native priority/severity
	 * notion (PagerDuty, OpsGenie, ntfy, Pushover, Gotify) map it onto their own scale;
	 * the rest ignore it. The default is {@link Severity#DEFAULT} so an application that
	 * does not override this sees unchanged payloads.
	 * @return the severity; never {@code null}
	 * @since 1.0.0
	 */
	default Severity severity(E event) {
		return Severity.DEFAULT;
	}

}
