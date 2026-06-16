package org.alexmond.notify4j.spring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the notification subsystem, bound from {@code notifications.*}.
 *
 * <p>
 * Channels are declared as Apprise/shoutrrr-style URLs, e.g.
 * </p>
 * <pre>
 * notifications:
 *   urls:
 *     - slack://hooks.slack.com/services/T000/B000/XXXX
 *     - telegram://api.telegram.org/&lt;bot-token&gt;/&lt;chat-id&gt;
 * </pre>
 *
 * The default {@code ignore-changes} suppresses transitions into non-terminal states, so
 * channels fire on terminal SUCCESS/FAILED rather than PENDING/RUNNING.
 */
@ConfigurationProperties("notify4j")
public class NotificationProperties {

	/** Transitions ({@code OLD:NEW}, {@code *} wildcards) suppressed by default. */
	private static final List<String> DEFAULT_IGNORE = List.of("*:PENDING", "*:RUNNING", "*:ASSIGNED");

	/** Apprise/shoutrrr-style channel URLs (see {@code NotifierUrlParser}). */
	private List<String> urls = new ArrayList<>();

	/** Status transitions to suppress, applied to every channel. */
	private List<String> ignoreChanges = new ArrayList<>(DEFAULT_IGNORE);

	/** Whether to always include a logging sink (handy with no channels configured). */
	private boolean log = true;

	/**
	 * Email channel (SMTP via the standard {@code spring.mail.*} config); off unless
	 * {@code to} is set.
	 */
	private final Email email = new Email();

	public List<String> getUrls() {
		return urls;
	}

	public void setUrls(List<String> urls) {
		this.urls = urls;
	}

	public List<String> getIgnoreChanges() {
		return ignoreChanges;
	}

	public void setIgnoreChanges(List<String> ignoreChanges) {
		this.ignoreChanges = ignoreChanges;
	}

	public boolean isLog() {
		return log;
	}

	public void setLog(boolean log) {
		this.log = log;
	}

	public Email getEmail() {
		return email;
	}

	/** Email channel settings; SMTP transport itself comes from {@code spring.mail.*}. */
	public static class Email {

		/** Recipients; the channel is inactive while empty. */
		private List<String> to = new ArrayList<>();

		/** From address; falls back to {@code spring.mail} defaults when blank. */
		private String from;

		/** Prepended to the subject line. */
		private String subjectPrefix = "[builder]";

		public List<String> getTo() {
			return to;
		}

		public void setTo(List<String> to) {
			this.to = to;
		}

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getSubjectPrefix() {
			return subjectPrefix;
		}

		public void setSubjectPrefix(String subjectPrefix) {
			this.subjectPrefix = subjectPrefix;
		}

	}

}
