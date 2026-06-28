package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The facade, when reminders are configured, re-fires an entity stuck in a reminder
 * status through its channels — and does so even though each channel has a transition
 * filter that would otherwise suppress the repeated status (the facade forgets the
 * transition first).
 */
class NotificationsReminderTest {

	private static final NotificationAdapter<String> ADAPTER = new NotificationAdapter<>() {
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

	@Test
	void remindersReFireAStuckEntityThroughATransitionFilteredChannel() throws Exception {
		CountDownLatch delivered = new CountDownLatch(3); // initial send + at least 2
															// reminders
		// a channel that dedupes by transition, like the real HTTP channels
		AbstractTransitionNotifier<String> channel = new AbstractTransitionNotifier<>() {
			@Override
			protected Object entityId(String e) {
				return e;
			}

			@Override
			protected String status(String e) {
				return e;
			}

			@Override
			protected void doNotify(String e) {
				delivered.countDown();
			}
		};
		NotificationsConfig config = NotificationsConfig.builder()
			.includeLog(false)
			.reminders(Set.of("FAILED"), Duration.ofMillis(1), Duration.ofMillis(20))
			.build();

		try (Notifications<String> facade = new Notifications<>(List.of(), ADAPTER, List.of(channel), config)) {
			facade.send("FAILED");
			// without the forget-before-reminder, the channel's filter would suppress
			// every
			// repeat and this would time out
			assertThat(delivered.await(3, TimeUnit.SECONDS)).isTrue();
		}
	}

}
