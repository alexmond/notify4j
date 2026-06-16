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

	void notify(E event);

}
