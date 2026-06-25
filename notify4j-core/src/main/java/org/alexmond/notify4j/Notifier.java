package org.alexmond.notify4j;

/**
 * Sends a notification for an event. Generic over the event type {@code E} so this core
 * stays domain-agnostic and reusable (no CI/pipeline coupling). Implementations should
 * never throw — a failing channel must not break the caller; see
 * {@link AbstractEventNotifier}.
 *
 * @param <E> the application's event type (e.g. a pipeline-run event)
 */
@FunctionalInterface
public interface Notifier<E> {

	/**
	 * Deliver a notification for {@code event}. Must not throw — a failing channel is
	 * expected to log and swallow so it never breaks the caller or sibling channels.
	 * @param event the event to notify about
	 */
	void notify(E event);

}
