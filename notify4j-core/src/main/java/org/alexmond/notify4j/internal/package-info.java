/**
 * Internal channel implementations — <strong>not</strong> part of the public API.
 *
 * <p>
 * The concrete per-channel notifiers (Slack, Teams, PagerDuty, …) live here and are built
 * only by {@link org.alexmond.notify4j.NotifierUrlParser} from Apprise/shoutrrr-style
 * channel URLs. Configure channels through URLs and the
 * {@link org.alexmond.notify4j.Notifications} facade — do not depend on these classes or
 * their constructors directly; they may change, move, or be removed in any release
 * without notice and are excluded from the project's semantic-versioning guarantees.
 */
package org.alexmond.notify4j.internal;
