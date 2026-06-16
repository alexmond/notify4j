package org.alexmond.notify4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decorates a delegate notifier and suppresses events matching any active
 * {@link NotificationFilter} (e.g. "mute this pipeline for 1h"). Expired filters are
 * pruned lazily.
 */
public class FilteringNotifier<E> implements Notifier<E> {

	private final Notifier<E> delegate;

	private final List<NotificationFilter<E>> filters = new CopyOnWriteArrayList<>();

	public FilteringNotifier(Notifier<E> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void notify(E event) {
		if (isMuted(event)) {
			return;
		}
		delegate.notify(event);
	}

	/**
	 * Whether any active filter currently mutes this event (prunes expired filters as a
	 * side effect).
	 */
	public boolean isMuted(E event) {
		filters.removeIf(NotificationFilter::isExpired);
		for (NotificationFilter<E> f : filters) {
			if (f.mutes(event)) {
				return true;
			}
		}
		return false;
	}

	public void addFilter(NotificationFilter<E> filter) {
		filters.removeIf((f) -> f.id().equals(filter.id()));
		filters.add(filter);
	}

	public boolean removeFilter(String id) {
		return filters.removeIf((f) -> f.id().equals(id));
	}

	/** Active (non-expired) filters. */
	public List<NotificationFilter<E>> getFilters() {
		filters.removeIf(NotificationFilter::isExpired);
		return List.copyOf(filters);
	}

}
