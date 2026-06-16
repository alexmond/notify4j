package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Covers the application-facing {@link Notifications} facade and its
 * {@link NotificationsFactory}.
 */
class NotificationsTest {

	static final NotificationAdapter<Evt> ADAPTER = new NotificationAdapter<>() {
		@Override
		public Object id(Evt e) {
			return e.id();
		}

		@Override
		public String status(Evt e) {
			return e.status();
		}

		@Override
		public String message(Evt e) {
			return e.name() + ":" + e.status();
		}
	};

	@Test
	void fansOutToExtraNotifiersAndCountsTheLogSink() {
		Recorder rec = new Recorder();
		Notifications<Evt> n = new Notifications<>(null, ADAPTER, List.of(rec), List.of(), true);

		// log sink + the one extra notifier
		assertThat(n.channelCount()).isEqualTo(2);

		n.send(new Evt(1, "SUCCESS", "p"));
		assertThat(rec.seen).extracting(Evt::id).containsExactly(1L);
	}

	@Test
	void aFailingChannelDoesNotStopTheOthers() {
		Recorder rec = new Recorder();
		Notifier<Evt> boom = (e) -> {
			throw new RuntimeException("boom");
		};
		Notifications<Evt> n = new Notifications<>(null, ADAPTER, List.of(boom, rec), List.of(), false);

		n.send(new Evt(1, "FAILED", "p"));
		assertThat(rec.seen).hasSize(1);
	}

	@Test
	void runtimeMuteSuppressesUntilRemoved() {
		Recorder rec = new Recorder();
		Notifications<Evt> n = new Notifications<>(null, ADAPTER, List.of(rec), List.of(), false);

		n.addFilter(new ExpiringFilter<>("mute-p", (e) -> e.name().equals("p"), Instant.now().plusSeconds(60)));
		n.send(new Evt(1, "FAILED", "p")); // muted
		n.send(new Evt(2, "FAILED", "q")); // not muted
		assertThat(rec.seen).extracting(Evt::id).containsExactly(2L);
		assertThat(n.filters()).extracting(NotificationFilter::id).containsExactly("mute-p");

		assertThat(n.removeFilter("mute-p")).isTrue();
		n.send(new Evt(3, "FAILED", "p")); // un-muted now
		assertThat(rec.seen).extracting(Evt::id).containsExactly(2L, 3L);
	}

	@Test
	void addNotifierRegistersAnUntaggedChannelAtRuntime() {
		Notifications<Evt> n = new Notifications<>(null, ADAPTER, List.of(), List.of(), false);
		assertThat(n.channelCount()).isZero();

		Recorder rec = new Recorder();
		n.addNotifier(rec);
		n.send(new Evt(1, "SUCCESS", "p"));
		assertThat(n.channelCount()).isEqualTo(1);
		assertThat(rec.seen).hasSize(1);
	}

	@Test
	void factoryBuildsFacadesWithSharedDefaults() {
		NotificationsFactory<Evt> factory = new NotificationsFactory<>(ADAPTER, List.of("*:RUNNING"), true);
		assertThat(factory.adapter()).isSameAs(ADAPTER);

		Recorder rec = new Recorder();
		Notifications<Evt> n = factory.create(List.of(), List.of(rec));
		assertThat(n.channelCount()).isEqualTo(2); // log sink (includeLog) + extra

		Notifications<Evt> plain = factory.create(List.of());
		assertThat(plain.channelCount()).isEqualTo(1); // just the log sink
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
