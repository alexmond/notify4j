package org.alexmond.notify4j.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The email channel sends on a real transition and respects ignore-changes + the subject
 * prefix.
 */
class EmailNotifierTest {

	private EmailNotifier<Event> notifier(JavaMailSender sender) {
		return new EmailNotifier<>(sender, "ci@builder.local", List.of("dev@team.local"), "[builder]", Event::id,
				Event::status, Event::message, List.of("*:RUNNING"));
	}

	@Test
	void sendsOnTerminalTransitionWithPrefixedSubject() {
		JavaMailSender sender = mock(JavaMailSender.class);
		EmailNotifier<Event> notifier = notifier(sender);

		notifier.notify(new Event(7L, "RUNNING", "running")); // ignored transition
		verify(sender, never()).send(any(SimpleMailMessage.class));

		notifier.notify(new Event(7L, "SUCCESS", "Pipeline 'x' #7: SUCCESS"));

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(sender).send(captor.capture());
		SimpleMailMessage sent = captor.getValue();
		assertThat(sent.getTo()).containsExactly("dev@team.local");
		assertThat(sent.getFrom()).isEqualTo("ci@builder.local");
		assertThat(sent.getSubject()).isEqualTo("[builder] SUCCESS");
		assertThat(sent.getText()).contains("SUCCESS");
	}

	@Test
	void doesNothingWithNoRecipients() {
		JavaMailSender sender = mock(JavaMailSender.class);
		EmailNotifier<Event> notifier = new EmailNotifier<>(sender, "ci@x", List.of(), "[builder]", Event::id,
				Event::status, Event::message, List.of());

		notifier.notify(new Event(1L, "SUCCESS", "msg"));
		verify(sender, never()).send(any(SimpleMailMessage.class));
	}

	private record Event(Object id, String status, String message) {
	}

}
