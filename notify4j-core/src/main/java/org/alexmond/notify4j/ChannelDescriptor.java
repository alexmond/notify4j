package org.alexmond.notify4j;

import java.util.List;

/**
 * Describes a channel scheme for discovery/UI: its scheme, its ordered configuration
 * {@link ChannelField fields}, and whether it carries a secret. Obtained from
 * {@link ChannelCatalog}; not meant to be constructed by consumers.
 *
 * @param scheme the URL scheme (e.g. {@code slack}, {@code pagerduty})
 * @param fields the channel's fields, in render order
 * @param credentialBearing whether any field is a secret (so a UI can badge it and warn
 * on cleartext transport)
 * @since 1.1.0
 */
public record ChannelDescriptor(String scheme, List<ChannelField> fields, boolean credentialBearing) {
}
