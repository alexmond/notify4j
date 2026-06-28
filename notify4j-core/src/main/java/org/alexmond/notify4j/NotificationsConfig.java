package org.alexmond.notify4j;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Immutable, builder-built configuration for a {@link Notifications} facade: the
 * transition {@code ignoreChanges} patterns, whether to add the log sink, the shared
 * {@link HttpClientConfig} (client/timeouts/retry), an optional {@link Executor} for
 * asynchronous delivery, and an optional {@link NotificationMetrics} sink.
 *
 * <p>
 * This replaces the old telescoping constructors: new options become builder methods
 * (additive, non-breaking) instead of new constructor overloads. The {@code executor},
 * when supplied, is owned by the caller — {@link Notifications#close()} does not shut it
 * down.
 *
 * <pre>
 * var config = NotificationsConfig.builder()
 *         .ignoreChanges(List.of("*:RUNNING"))
 *         .http(HttpClientConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(10), 3, Duration.ofMillis(500)))
 *         .executor(myPool)
 *         .build();
 * </pre>
 *
 * @since 1.0.0
 */
public final class NotificationsConfig {

	private final List<String> ignoreChanges;

	private final boolean includeLog;

	private final HttpClientConfig http;

	private final Executor executor;

	private final NotificationMetrics metrics;

	private NotificationsConfig(Builder builder) {
		this.ignoreChanges = List.copyOf(builder.ignoreChanges);
		this.includeLog = builder.includeLog;
		this.http = (builder.http != null) ? builder.http : HttpClientConfig.defaults();
		this.executor = builder.executor;
		this.metrics = (builder.metrics != null) ? builder.metrics : NotificationMetrics.NOOP;
	}

	/**
	 * A fresh builder (defaults: no ignore-changes, log sink on, default HTTP, sync, no
	 * metrics).
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Config with all defaults. */
	public static NotificationsConfig defaults() {
		return builder().build();
	}

	/**
	 * Transition patterns suppressed per channel ({@code OLD:NEW}, {@code *} wildcards).
	 */
	public List<String> ignoreChanges() {
		return this.ignoreChanges;
	}

	/** Whether to add the always-on {@link LoggingNotifier} sink. */
	public boolean includeLog() {
		return this.includeLog;
	}

	/** Shared HTTP client, timeouts, and retry policy for the webhook-style channels. */
	public HttpClientConfig http() {
		return this.http;
	}

	/**
	 * Executor for asynchronous delivery, or {@code null} for synchronous. Caller-owned.
	 */
	public Executor executor() {
		return this.executor;
	}

	/**
	 * Sink for per-channel delivery metrics; never {@code null}
	 * ({@link NotificationMetrics#NOOP} default).
	 */
	public NotificationMetrics metrics() {
		return this.metrics;
	}

	/** Builder for {@link NotificationsConfig}. */
	public static final class Builder {

		private List<String> ignoreChanges = List.of();

		private boolean includeLog = true;

		private HttpClientConfig http = HttpClientConfig.defaults();

		private Executor executor;

		private NotificationMetrics metrics = NotificationMetrics.NOOP;

		private Builder() {
		}

		public Builder ignoreChanges(List<String> ignoreChanges) {
			this.ignoreChanges = (ignoreChanges != null) ? ignoreChanges : List.of();
			return this;
		}

		public Builder includeLog(boolean includeLog) {
			this.includeLog = includeLog;
			return this;
		}

		public Builder http(HttpClientConfig http) {
			this.http = http;
			return this;
		}

		/** Deliver on this executor (caller-owned); omit for synchronous delivery. */
		public Builder executor(Executor executor) {
			this.executor = executor;
			return this;
		}

		public Builder metrics(NotificationMetrics metrics) {
			this.metrics = metrics;
			return this;
		}

		public NotificationsConfig build() {
			return new NotificationsConfig(this);
		}

	}

}
