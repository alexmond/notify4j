package org.alexmond.notify4j;

import java.util.List;

/**
 * Notifies only on meaningful status <em>transitions</em>, delegating the gating to a
 * {@link TransitionFilter}. Subclasses supply {@link #entityId} and {@link #status} for
 * the event type. (Channel notifiers that take functions instead of subclassing use a
 * {@link TransitionFilter} directly — see {@link AbstractHttpNotifier}.)
 */
public abstract class AbstractTransitionNotifier<E> extends AbstractEventNotifier<E> {

	private final TransitionFilter filter = new TransitionFilter();

	protected abstract Object entityId(E event);

	protected abstract String status(E event);

	@Override
	protected boolean shouldNotify(E event) {
		return filter.allow(entityId(event), status(event));
	}

	protected void forget(Object entityId) {
		filter.forget(entityId);
	}

	public List<String> getIgnoreChanges() {
		return filter.getIgnoreChanges();
	}

	public void setIgnoreChanges(List<String> ignoreChanges) {
		filter.setIgnoreChanges(ignoreChanges);
	}

}
