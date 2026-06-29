package org.alexmond.notify4j.sample;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.alexmond.notify4j.ExpiringFilter;
import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Sample app showcasing notify4j: declare a {@link NotificationAdapter} bean for your
 * event type and the starter auto-configures a {@link Notifications} facade from
 * {@code notify4j.urls} (plus the always-on log sink). With no URLs configured the demo
 * still fans out to the log sink, so it runs out of the box. The startup demo exercises
 * the headline features — runtime mute, tag routing, reminders, and metrics. Uncomment a
 * channel in {@code application.yml} to deliver for real.
 */
@SpringBootApplication
public class Notify4jSampleApplication {

	private static final Logger log = LoggerFactory.getLogger(Notify4jSampleApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(Notify4jSampleApplication.class, args);
	}

	/**
	 * The one piece every app supplies: how to read an id/status/message off its own
	 * event type. Here the event is just an {@code "id:STATUS"} string for brevity.
	 */
	@Bean
	NotificationAdapter<String> sampleAdapter() {
		return new NotificationAdapter<>() {
			@Override
			public Object id(String event) {
				return event.split(":", 2)[0];
			}

			@Override
			public String status(String event) {
				return event.split(":", 2)[1];
			}

			@Override
			public String message(String event) {
				return "sample event " + event;
			}
		};
	}

	/**
	 * A {@link MeterRegistry} makes the starter wire per-channel delivery metrics
	 * ({@code notify4j.notifications}). A real app usually gets one from Spring Boot
	 * Actuator; the sample registers a simple in-memory one so the demo can print counts.
	 */
	@Bean
	MeterRegistry meterRegistry() {
		return new SimpleMeterRegistry();
	}

	/** Exercise the headline features on startup, then print the metrics. */
	@Bean
	CommandLineRunner demo(Notifications<String> facade, MeterRegistry registry) {
		return (args) -> {
			log.info("notify4j sample: {} channel(s) configured", facade.channelCount());

			// 1) plain delivery — fans out to every channel (here, the log sink)
			facade.send("build-42:SUCCESS");

			// 2) runtime mute — suppress an entity for a while, then lift it
			facade.addFilter(new ExpiringFilter<>("mute-build-7", (e) -> e.startsWith("build-7:"),
					Instant.now().plus(1, ChronoUnit.HOURS)));
			log.info("muted build-7; the next send is suppressed");
			facade.send("build-7:FAILED"); // suppressed by the mute
			facade.removeFilter("mute-build-7");
			facade.send("build-7:FAILED"); // delivered now that the mute is gone

			// 3) tag routing — only channels tagged "failed" (and untagged ones like the
			// log
			// sink) receive this; configure e.g. pagerduty://...?tags=failed to see it
			// routed
			facade.send("svc-3:FAILED", List.of("failed"));

			// 4) reminders — a FAILED entity that stays unresolved is re-notified on the
			// schedule in application.yml (notify4j.reminders.*); wait to see it re-fire
			facade.send("svc-9:FAILED");
			log.info("armed a reminder for svc-9; waiting for it to re-fire…");
			Thread.sleep(600);

			// 5) metrics — per-channel delivery outcomes
			log.info("delivery metrics:");
			registry.find("notify4j.notifications")
				.counters()
				.forEach((c) -> log.info("  channel={} outcome={} count={}", c.getId().getTag("channel"),
						c.getId().getTag("outcome"), (long) c.count()));
		};
	}

}
