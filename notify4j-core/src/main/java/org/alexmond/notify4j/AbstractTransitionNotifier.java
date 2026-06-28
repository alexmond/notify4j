package org.alexmond.notify4j;

import java.util.List;

/**
 * Notifies only on meaningful status <em>transitions</em>, delegating the gating to a
 * {@link TransitionFilter}. Subclasses supply {@link #entityId} and {@link #status} for
 * the event type. (Channel notifiers that take functions instead of subclassing use a
 * {@link TransitionFilter} directly — see {@link AbstractHttpNotifier}.)
 *
 * @param <E> the application's event type
 * @since 1.0.0
 */
public abstract class AbstractTransitionNotifier<E> extends AbstractEventNotifier<E> {

	private final TransitionFilter filter = new TransitionFilter();

	/** The stable identity of the event's subject, used to track status transitions. */
	protected abstract Object entityId(E event);

	/** The current status of the subject (e.g. {@code SUCCESS}, {@code FAILED}). */
	protected abstract String status(E event);

	@Override
	protected boolean shouldNotify(E event) {
		return filter.allow(entityId(event), status(event));
	}

	/**
	 * Drop the tracked status for {@code entityId}, so its next event is treated as
	 * fresh.
	 */
	protected void forget(Object entityId) {
		filter.forget(entityId);
	}

	@Override
	protected void forgetTransition(Object entityId) {
		filter.forget(entityId);
	}

	public List<String> getIgnoreChanges() {
		return filter.getIgnoreChanges();
	}

	public void setIgnoreChanges(List<String> ignoreChanges) {
		filter.setIgnoreChanges(ignoreChanges);
	}

}
