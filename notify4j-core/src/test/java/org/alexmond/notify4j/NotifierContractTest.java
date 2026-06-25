package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * The {@link Notifier} "never throw" contract: even a {@code shouldNotify} that throws
 * (e.g. a misbehaving adapter) must be isolated, not propagated to the caller.
 */
class NotifierContractTest {

	@Test
	void shouldNotifyThrowingIsSwallowed() {
		AbstractEventNotifier<String> notifier = new AbstractEventNotifier<>() {
			@Override
			protected boolean shouldNotify(String event) {
				throw new IllegalStateException("boom from shouldNotify");
			}

			@Override
			protected void doNotify(String event) {
				// unreached
			}
		};
		assertThatCode(() -> notifier.notify("x")).doesNotThrowAnyException();
	}

}
