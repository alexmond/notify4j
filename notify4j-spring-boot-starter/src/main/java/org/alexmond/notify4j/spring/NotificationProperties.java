package org.alexmond.notify4j.spring;

import java.time.Duration;
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

	/** HTTP client settings shared by the webhook-style channels. */
	private final Http http = new Http();

	/** Asynchronous (non-blocking) delivery settings. */
	private final Async async = new Async();

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

	public Http getHttp() {
		return http;
	}

	public Async getAsync() {
		return async;
	}

	/** Email channel settings; SMTP transport itself comes from {@code spring.mail.*}. */
	public static class Email {

		/** Recipients; the channel is inactive while empty. */
		private List<String> to = new ArrayList<>();

		/** From address; falls back to {@code spring.mail} defaults when blank. */
		private String from;

		/** Prepended to the subject line. */
		private String subjectPrefix = "[notify4j]";

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

	/**
	 * HTTP timeouts for the webhook-style channels (Slack, Teams, Discord, webhook,
	 * Telegram, ntfy, PagerDuty, OpsGenie). A single {@code HttpClient} is shared across
	 * them.
	 */
	public static class Http {

		/** Connection timeout for outbound channel requests. */
		private Duration connectTimeout = Duration.ofSeconds(10);

		/** Read (per-request) timeout for outbound channel requests. */
		private Duration readTimeout = Duration.ofSeconds(10);

		/**
		 * Total delivery attempts per channel on transient failures
		 * (5xx/429/IOException); 1 disables retry.
		 */
		private int maxAttempts = 3;

		/** Base backoff between retries (doubled each attempt, capped). */
		private Duration retryBackoff = Duration.ofMillis(500);

		public Duration getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public Duration getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		public Duration getRetryBackoff() {
			return retryBackoff;
		}

		public void setRetryBackoff(Duration retryBackoff) {
			this.retryBackoff = retryBackoff;
		}

	}

	/**
	 * Asynchronous delivery: when enabled (default), each channel is dispatched on a
	 * shared thread pool so {@code send} returns immediately and a slow channel can't
	 * block the caller or its siblings. Disable for synchronous delivery.
	 */
	public static class Async {

		/** Deliver off the caller thread on a shared pool. */
		private boolean enabled = true;

		/** Size of the shared delivery thread pool. */
		private int poolSize = 4;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getPoolSize() {
			return poolSize;
		}

		public void setPoolSize(int poolSize) {
			this.poolSize = poolSize;
		}

	}

}
