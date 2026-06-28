package org.alexmond.notify4j;

/**
 * Optional severity of a notification, supplied by the application via
 * {@link NotificationAdapter#severity}. Channels that have a native priority/severity
 * notion (PagerDuty, OpsGenie, ntfy, Pushover, Gotify) map this onto their own scale;
 * channels without one ignore it.
 *
 * <p>
 * The default is {@link #DEFAULT}, which means "no explicit severity": a channel keeps
 * its historical behaviour (e.g. omits a priority field) so an application that does not
 * override {@code severity} sees unchanged payloads. The remaining levels are ordered
 * from least to most urgent.
 *
 * @since 1.0.0
 */
public enum Severity {

	/** No explicit severity; channels fall back to their own default. */
	DEFAULT,

	/** Informational. */
	INFO,

	/** A warning that may need attention. */
	WARNING,

	/** An error. */
	ERROR,

	/** A critical, page-worthy condition. */
	CRITICAL

}
