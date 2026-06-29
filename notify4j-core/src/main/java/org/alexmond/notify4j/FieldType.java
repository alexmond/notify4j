package org.alexmond.notify4j;

/**
 * How a channel configuration field should be rendered/entered in a UI.
 *
 * <p>
 * This enum may gain values in a future release; consumers that switch on it
 * <strong>must</strong> handle unknown values (treat them like {@link #TEXT}) rather than
 * relying on exhaustiveness.
 *
 * @since 1.1.0
 */
public enum FieldType {

	/** Free text (the default rendering). */
	TEXT,

	/** A full URL/endpoint (the channel's whole webhook URL). */
	URL

}
