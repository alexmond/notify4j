package org.alexmond.notify4j;

import java.util.List;

/**
 * Describes a channel scheme for discovery/UI: its scheme, a display name, its ordered
 * configuration {@link ChannelField fields}, and whether it carries a secret. Obtained
 * from {@link ChannelCatalog}; not meant to be constructed by consumers.
 *
 * @param scheme the URL scheme (e.g. {@code slack}, {@code pagerduty})
 * @param displayName a human-readable channel name for a picker (e.g. {@code "Slack"},
 * {@code "PagerDuty"})
 * @param fields the channel's fields, in render order
 * @param credentialBearing whether any field is a secret (so a UI can badge it and warn
 * on cleartext transport)
 * @param docsUrl a link to the provider/setup documentation for this channel, or
 * {@code null}
 * @since 1.1.0
 */
public record ChannelDescriptor(String scheme, String displayName, List<ChannelField> fields, boolean credentialBearing,
		String docsUrl) {
}
