package org.alexmond.notify4j.spring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the notification subsystem, bound from {@code notify4j.*}.
 *
 * <p>
 * Channels are declared as Apprise/shoutrrr-style URLs, e.g.
 * </p>
 * <pre>
 * notify4j:
 *   urls:
 *     - slack://hooks.slack.com/services/T000/B000/XXXX
 *     - telegram://api.telegram.org/&lt;bot-token&gt;/&lt;chat-id&gt;
 *   http:
 *     max-attempts: 3        # retry transient 5xx/429/IOException
 *   async:
 *     enabled: true          # deliver off the caller thread
 * </pre>
 *
 * The default {@code ignore-changes} suppresses transitions into non-terminal states
 * ({@code PENDING}/{@code RUNNING}/{@code ASSIGNED}), so channels fire on terminal
 * SUCCESS/FAILED rather than intermediate states. See also {@link Http} (timeouts +
 * retry) and {@link Async} (non-blocking delivery).
 *
 * @since 1.0.0
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

	/** Reminder (re-notify for stuck entities) settings; off unless enabled. */
	private final Reminders reminders = new Reminders();

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

	public Reminders getReminders() {
		return reminders;
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

		/**
		 * Bound on the delivery queue, so a burst or a persistently-slow channel can't
		 * grow it without limit and OOM the host. When the pool and queue are both full,
		 * new deliveries are handled by {@link #rejectionPolicy}.
		 */
		private int queueCapacity = 1000;

		/** What to do when the pool and queue are both full. */
		private RejectionPolicy rejectionPolicy = RejectionPolicy.DROP;

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

		public int getQueueCapacity() {
			return queueCapacity;
		}

		public void setQueueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

		public RejectionPolicy getRejectionPolicy() {
			return rejectionPolicy;
		}

		public void setRejectionPolicy(RejectionPolicy rejectionPolicy) {
			this.rejectionPolicy = rejectionPolicy;
		}

	}

	/**
	 * How to handle a delivery when the async pool and its queue are both full.
	 */
	public enum RejectionPolicy {

		/**
		 * Drop the delivery (default). Never blocks the caller and never grows memory;
		 * the drop is logged and recorded via {@code NotificationMetrics.recordDropped}.
		 * Best for an alerting library that must not harm its host under back-pressure.
		 */
		DROP,

		/**
		 * Run the delivery on the caller's thread. Applies back-pressure (the caller
		 * slows down) instead of dropping, at the cost of momentarily reintroducing
		 * caller-thread blocking — choose this when losing a notification is worse than a
		 * slow caller.
		 */
		CALLER_RUNS

	}

	/**
	 * Reminders re-notify every channel for an entity that stays in a configured status
	 * (e.g. an app stuck {@code DOWN}) until it resolves. Off by default; when enabled,
	 * {@link #statuses} must list the statuses that arm a reminder.
	 */
	public static class Reminders {

		/** Enable periodic re-notification for stuck entities. */
		private boolean enabled;

		/** Statuses that arm a reminder (e.g. {@code FAILED}, {@code DOWN}). */
		private List<String> statuses = new ArrayList<>();

		/** How long an entity must stay in a reminder status before it is re-notified. */
		private Duration period = Duration.ofHours(1);

		/** How often the scheduler checks for due reminders. */
		private Duration checkInterval = Duration.ofMinutes(1);

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public List<String> getStatuses() {
			return statuses;
		}

		public void setStatuses(List<String> statuses) {
			this.statuses = statuses;
		}

		public Duration getPeriod() {
			return period;
		}

		public void setPeriod(Duration period) {
			this.period = period;
		}

		public Duration getCheckInterval() {
			return checkInterval;
		}

		public void setCheckInterval(Duration checkInterval) {
			this.checkInterval = checkInterval;
		}

	}

}
