package org.alexmond.notify4j.sample;

import org.alexmond.notify4j.NotificationAdapter;
import org.alexmond.notify4j.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Minimal sample app demonstrating notify4j: declare a {@link NotificationAdapter} bean
 * for your event type and the starter auto-configures a {@link Notifications} facade from
 * {@code notify4j.urls} (plus the always-on log sink). Run it and watch the demo event
 * fan out to whatever channels {@code application.yml} configures.
 */
@SpringBootApplication
public class Notify4jSampleApplication {

	private static final Logger log = LoggerFactory.getLogger(Notify4jSampleApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(Notify4jSampleApplication.class, args);
	}

	/**
	 * The one piece every app supplies: how to read an id/status/message off its own
	 * event type. Here the event is just a {@code "id:STATUS"} string for brevity.
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

	/** Fire one demo event through the auto-configured facade on startup. */
	@Bean
	CommandLineRunner demo(ObjectProvider<Notifications<String>> notifications) {
		return (args) -> {
			Notifications<String> facade = notifications.getIfAvailable();
			if (facade == null) {
				log.info("no Notifications facade configured");
				return;
			}
			log.info("sending demo event over {} channel(s)", facade.channelCount());
			facade.send("build-42:SUCCESS");
		};
	}

}
