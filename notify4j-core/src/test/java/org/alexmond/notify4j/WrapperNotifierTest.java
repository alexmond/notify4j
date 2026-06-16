package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class WrapperNotifierTest {

	@Test
	void filteringNotifierMutesMatchingEventsUntilExpiry() {
		Recorder recorder = new Recorder();
		FilteringNotifier<Evt> filtering = new FilteringNotifier<>(recorder);

		// mute pipeline "noisy" with an already-expired filter -> not muted
		filtering
			.addFilter(new ExpiringFilter<>("expired", (e) -> e.name().equals("noisy"), Instant.now().minusSeconds(1)));
		filtering.notify(new Evt(1, "FAILED", "noisy"));
		assertThat(recorder.seen).hasSize(1);

		// active mute -> suppressed
		filtering.addFilter(
				new ExpiringFilter<>("mute-noisy", (e) -> e.name().equals("noisy"), Instant.now().plusSeconds(60)));
		filtering.notify(new Evt(2, "FAILED", "noisy")); // muted
		filtering.notify(new Evt(3, "FAILED", "other")); // not muted
		assertThat(recorder.seen).extracting(Evt::id).containsExactly(1L, 3L);

		assertThat(filtering.removeFilter("mute-noisy")).isTrue();
		filtering.notify(new Evt(4, "FAILED", "noisy")); // un-muted now
		assertThat(recorder.seen).extracting(Evt::id).containsExactly(1L, 3L, 4L);
	}

	@Test
	void remindingNotifierRefiresWhileBadAndStopsWhenResolved() {
		Recorder recorder = new Recorder();
		RemindingNotifier<Evt> reminding = new RemindingNotifier<>(recorder, Evt::id, Evt::status, Set.of("FAILED"),
				Duration.ofMinutes(10));

		Instant t0 = Instant.now();
		reminding.notify(new Evt(1, "FAILED", "p")); // delivered + tracked
		assertThat(recorder.seen).hasSize(1);
		assertThat(reminding.reminderCount()).isEqualTo(1);

		reminding.checkReminders(t0.plus(Duration.ofMinutes(5))); // too soon
		assertThat(recorder.seen).hasSize(1);

		reminding.checkReminders(t0.plus(Duration.ofMinutes(11))); // period elapsed ->
																	// re-fire
		assertThat(recorder.seen).hasSize(2);

		reminding.notify(new Evt(1, "SUCCESS", "p")); // resolved -> untracked
		assertThat(reminding.reminderCount()).isZero();
		reminding.checkReminders(t0.plus(Duration.ofMinutes(30))); // nothing to re-fire
		assertThat(recorder.seen).hasSize(3); // only the SUCCESS delivery above
	}

	record Evt(long id, String status, String name) {
	}

	static class Recorder implements Notifier<Evt> {

		final List<Evt> seen = new CopyOnWriteArrayList<>();

		@Override
		public void notify(Evt e) {
			seen.add(e);
		}

	}

}
