package org.alexmond.notify4j.spring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.alexmond.notify4j.NotificationMetrics;

/**
 * Records notification outcomes as Micrometer counters {@code notify4j.notifications}
 * tagged by {@code channel} and {@code outcome} (sent/failed/suppressed). Wired by the
 * starter only when a {@link MeterRegistry} bean is present.
 */
class MicrometerNotificationMetrics implements NotificationMetrics {

	private static final String METER = "notify4j.notifications";

	private final MeterRegistry registry;

	private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

	MicrometerNotificationMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void recordSent(String channel) {
		counter(channel, "sent").increment();
	}

	@Override
	public void recordFailed(String channel) {
		counter(channel, "failed").increment();
	}

	@Override
	public void recordSuppressed(String channel) {
		counter(channel, "suppressed").increment();
	}

	@Override
	public void recordDropped(String channel) {
		counter(channel, "dropped").increment();
	}

	private Counter counter(String channel, String outcome) {
		return this.counters.computeIfAbsent(channel + '/' + outcome,
				(key) -> Counter.builder(METER)
					.tag("channel", channel)
					.tag("outcome", outcome)
					.description("notify4j notification deliveries")
					.register(this.registry));
	}

}
