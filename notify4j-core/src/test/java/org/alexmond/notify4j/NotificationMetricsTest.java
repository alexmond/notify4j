package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationMetricsTest {

	@Test
	void recordsSentFailedAndSuppressed() {
		Recording metrics = new Recording();
		TestNotifier notifier = new TestNotifier();
		notifier.setMetrics(metrics);

		notifier.notify("ok"); // sent
		notifier.fail = true;
		notifier.notify("bad"); // failed
		notifier.fail = false;
		notifier.allow = false;
		notifier.notify("x"); // suppressed

		assertThat(metrics.sent).containsEntry("TestNotifier", 1);
		assertThat(metrics.failed).containsEntry("TestNotifier", 1);
		assertThat(metrics.suppressed).containsEntry("TestNotifier", 1);
	}

	@Test
	void facadePropagatesMetricsToChannels() {
		Recording metrics = new Recording();
		NotificationAdapter<String> adapter = new NotificationAdapter<>() {
			@Override
			public Object id(String e) {
				return e;
			}

			@Override
			public String status(String e) {
				return e;
			}

			@Override
			public String message(String e) {
				return e;
			}
		};
		Notifications<String> facade = new Notifications<>(List.of(), adapter, List.of(new TestNotifier()),
				NotificationsConfig.builder().includeLog(false).metrics(metrics).build());

		facade.send("hello");

		assertThat(metrics.sent).containsEntry("TestNotifier", 1);
	}

	/** Metrics sink that counts per channel for assertions. */
	static final class Recording implements NotificationMetrics {

		final Map<String, Integer> sent = new HashMap<>();

		final Map<String, Integer> failed = new HashMap<>();

		final Map<String, Integer> suppressed = new HashMap<>();

		@Override
		public void recordSent(String channel) {
			this.sent.merge(channel, 1, Integer::sum);
		}

		@Override
		public void recordFailed(String channel) {
			this.failed.merge(channel, 1, Integer::sum);
		}

		@Override
		public void recordSuppressed(String channel) {
			this.suppressed.merge(channel, 1, Integer::sum);
		}

	}

	/** A controllable notifier whose outcome the test drives. */
	static final class TestNotifier extends AbstractEventNotifier<String> {

		boolean fail;

		boolean allow = true;

		@Override
		protected boolean shouldNotify(String event) {
			return this.allow;
		}

		@Override
		protected void doNotify(String event) {
			if (this.fail) {
				throw new IllegalStateException("boom");
			}
		}

	}

}
