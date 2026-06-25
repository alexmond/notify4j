package org.alexmond.notify4j.spring;

import java.util.List;
import java.util.function.Function;
import org.alexmond.notify4j.AbstractEventNotifier;
import org.alexmond.notify4j.TransitionFilter;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends a plain-text email per status transition via Spring's {@link JavaMailSender}.
 * Unlike the URL channels this lives in the starter (it is Spring-mail-coupled and SMTP
 * is configured via the standard {@code spring.mail.*} properties, not a channel URL).
 * Applies the same {@link TransitionFilter} as the other channels so it fires on real
 * status changes only. The subject is {@code subjectPrefix + " " + status} and the body
 * is the message.
 *
 * @param <E> the application's event type
 */
public class EmailNotifier<E> extends AbstractEventNotifier<E> {

	private final JavaMailSender mailSender;

	private final String from;

	private final List<String> to;

	private final String subjectPrefix;

	private final Function<E, Object> idFn;

	private final Function<E, String> statusFn;

	private final Function<E, String> messageFn;

	private final TransitionFilter filter;

	/**
	 * @param mailSender Spring mail sender (SMTP from {@code spring.mail.*})
	 * @param from from address; {@code null}/blank falls back to the mail defaults
	 * @param to recipients; the channel is inactive while empty
	 * @param subjectPrefix prepended to the subject ({@code null} treated as empty)
	 * @param idFn reads the entity id (transition key) from an event
	 * @param statusFn reads the status from an event (used as the subject)
	 * @param messageFn reads the human message from an event (used as the body)
	 * @param ignoreChanges transition patterns to suppress (see {@link TransitionFilter})
	 */
	public EmailNotifier(JavaMailSender mailSender, String from, List<String> to, String subjectPrefix,
			Function<E, Object> idFn, Function<E, String> statusFn, Function<E, String> messageFn,
			List<String> ignoreChanges) {
		this.mailSender = mailSender;
		this.from = from;
		this.to = to;
		this.subjectPrefix = (subjectPrefix != null) ? subjectPrefix : "";
		this.idFn = idFn;
		this.statusFn = statusFn;
		this.messageFn = messageFn;
		this.filter = new TransitionFilter(ignoreChanges);
	}

	@Override
	protected boolean shouldNotify(E event) {
		return to != null && !to.isEmpty() && filter.allow(idFn.apply(event), statusFn.apply(event));
	}

	@Override
	protected void doNotify(E event) {
		SimpleMailMessage message = new SimpleMailMessage();
		if (from != null && !from.isBlank()) {
			message.setFrom(from);
		}
		message.setTo(to.toArray(new String[0]));
		message.setSubject((subjectPrefix.isBlank() ? "" : subjectPrefix + " ") + statusFn.apply(event));
		message.setText(messageFn.apply(event));
		mailSender.send(message);
	}

}
