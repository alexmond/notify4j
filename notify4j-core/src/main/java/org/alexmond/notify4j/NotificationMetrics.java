package org.alexmond.notify4j;

/**
 * Callback for per-channel delivery outcomes, so operators can confirm notifications are
 * actually going out. The core has no metrics dependency — it records against this SPI;
 * the Spring Boot starter supplies a Micrometer-backed implementation when a
 * {@code MeterRegistry} is present. Recorded inside {@link AbstractEventNotifier} (where
 * success/failure is known, since a failing channel is swallowed and never rethrown). The
 * default {@link #NOOP} records nothing.
 */
public interface NotificationMetrics {

	/** A successful delivery on {@code channel}. */
	void recordSent(String channel);

	/**
	 * A delivery on {@code channel} that failed (after any retries) and was swallowed.
	 */
	void recordFailed(String channel);

	/**
	 * An event suppressed by {@code channel} (e.g. its transition filter — not a real
	 * change).
	 */
	void recordSuppressed(String channel);

	/** No-op metrics; the default until a registry-backed implementation is wired in. */
	NotificationMetrics NOOP = new NotificationMetrics() {
		@Override
		public void recordSent(String channel) {
		}

		@Override
		public void recordFailed(String channel) {
		}

		@Override
		public void recordSuppressed(String channel) {
		}
	};

}
