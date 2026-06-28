package org.alexmond.notify4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateful gate that allows an event only on a meaningful status <em>transition</em>.
 * Tracks the last status per entity id and suppresses non-transitions and changes
 * matching {@code ignoreChanges} (patterns {@code OLD:NEW} with {@code *} wildcards).
 * Reusable by any notifier.
 *
 * @since 1.0.0
 */
public class TransitionFilter {

	private static final String UNKNOWN = "UNKNOWN";

	private final Map<Object, String> lastStatus = new ConcurrentHashMap<>();

	private List<String> ignoreChanges = new ArrayList<>();

	public TransitionFilter() {
	}

	public TransitionFilter(List<String> ignoreChanges) {
		this.ignoreChanges = (ignoreChanges != null) ? ignoreChanges : new ArrayList<>();
	}

	/** Record the transition and return whether it should be delivered. */
	public boolean allow(Object entityId, String status) {
		if (entityId == null) {
			// No stable identity to dedupe on — deliver and don't track.
			return true;
		}
		String to = (status != null) ? status : UNKNOWN;
		boolean[] deliver = { false };
		// compute() is atomic per key, so concurrent events for the same id can't
		// interleave
		// the read-decide-write and double-fire or lose a transition.
		lastStatus.compute(entityId, (key, from) -> {
			String prev = (from != null) ? from : UNKNOWN;
			deliver[0] = !prev.equals(to) && !ignored(prev, to);
			return to;
		});
		return deliver[0];
	}

	public void forget(Object entityId) {
		lastStatus.remove(entityId);
	}

	private boolean ignored(String from, String to) {
		for (String pattern : ignoreChanges) {
			int colon = pattern.indexOf(':');
			if (colon < 0) {
				continue;
			}
			String oldP = pattern.substring(0, colon).trim();
			String newP = pattern.substring(colon + 1).trim();
			if (("*".equals(oldP) || oldP.equals(from)) && ("*".equals(newP) || newP.equals(to))) {
				return true;
			}
		}
		return false;
	}

	public List<String> getIgnoreChanges() {
		return ignoreChanges;
	}

	public void setIgnoreChanges(List<String> ignoreChanges) {
		this.ignoreChanges = (ignoreChanges != null) ? ignoreChanges : new ArrayList<>();
	}

}
