package org.alexmond.notify4j;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fans an event out to several notifiers, isolating each one's failures. */
public class CompositeNotifier<E> implements Notifier<E> {

	private static final Logger log = LoggerFactory.getLogger(CompositeNotifier.class);

	private final List<Notifier<E>> delegates;

	public CompositeNotifier(List<Notifier<E>> delegates) {
		this.delegates = List.copyOf(delegates);
	}

	@Override
	public void notify(E event) {
		for (Notifier<E> delegate : delegates) {
			try {
				delegate.notify(event);
			}
			catch (RuntimeException ex) {
				log.warn("notifier {} failed: {}", delegate.getClass().getSimpleName(), ex.getMessage());
			}
		}
	}

	public int size() {
		return delegates.size();
	}

}
