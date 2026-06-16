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

}
