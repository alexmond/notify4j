package org.alexmond.notify4j;

/** Default sink: logs each event. Useful as a no-config default and for tests. */
public class LoggingNotifier<E> extends AbstractEventNotifier<E> {

	@Override
	protected void doNotify(E event) {
		log.info("notification: {}", event);
	}

}
