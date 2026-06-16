package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotifierCoreTest {

	@Test
	void compositeFansOutAndIsolatesFailures() {
		Recorder a = new Recorder();
		Notifier<Evt> boom = (e) -> {
			throw new RuntimeException("boom");
		};
		Recorder b = new Recorder();
		CompositeNotifier<Evt> composite = new CompositeNotifier<>(List.of(a, boom, b));

		composite.notify(new Evt(1, "SUCCESS"));

		// the failing notifier doesn't stop the others
		assertThat(a.seen).hasSize(1);
		assertThat(b.seen).hasSize(1);
	}

	@Test
	void transitionNotifierFiltersByStatusChange() {
		List<Evt> seen = new ArrayList<>();
		var notifier = new AbstractTransitionNotifier<Evt>() {
			@Override
			protected Object entityId(Evt e) {
				return e.id();
			}

			@Override
			protected String status(Evt e) {
				return e.status();
			}

			@Override
			protected void doNotify(Evt e) {
				seen.add(e);
			}
		};
		notifier.setIgnoreChanges(List.of("*:RUNNING", "*:PENDING"));

		notifier.notify(new Evt(1, "PENDING")); // ignored (->PENDING)
		notifier.notify(new Evt(1, "RUNNING")); // ignored (->RUNNING)
		notifier.notify(new Evt(1, "SUCCESS")); // delivered (RUNNING->SUCCESS)
		notifier.notify(new Evt(1, "SUCCESS")); // ignored (no transition)

		assertThat(seen).extracting(Evt::status).containsExactly("SUCCESS");
	}

	/** A run event for the test: an id and a status. */
	record Evt(long id, String status) {
	}

	static class Recorder extends AbstractEventNotifier<Evt> {

		final List<Evt> seen = new ArrayList<>();

		@Override
		protected void doNotify(Evt event) {
			seen.add(event);
		}

	}

}
