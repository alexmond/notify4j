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
		String from = lastStatus.put(entityId, status);
		if (from == null) {
			from = UNKNOWN;
		}
		if (from.equals(status)) {
			return false;
		}
		return !ignored(from, status);
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
