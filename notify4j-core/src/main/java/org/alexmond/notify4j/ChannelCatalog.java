package org.alexmond.notify4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only discovery API over notify4j's channels, so an application can render a "pick
 * a channel → fill the fields → save" UI data-driven instead of hardcoding the scheme
 * list, each channel's fields, which fields are secret, and how to assemble the
 * Apprise-style URL.
 *
 * <p>
 * notify4j owns URL assembly end-to-end: {@link #buildUrl} produces a URL the
 * {@code NotifierUrlParser} accepts (separators, default hosts, the {@code +http}/
 * {@code +https} transport, and {@code ?tags=} are all handled here — callers never
 * concatenate; a {@code URL}-typed field may be pasted with or without its
 * {@code http(s)://} prefix). {@link #parse} is the inverse for edit screens, returning
 * secrets {@link #MASKED_SECRET masked}; {@link #recompose} rebuilds an edited channel
 * while preserving any secret the operator left untouched. Obtain the built-in instance
 * via {@link #standard()}.
 *
 * @since 1.1.0
 */
public interface ChannelCatalog {

	/** The placeholder returned by {@link #parse} for a secret field that has a value. */
	String MASKED_SECRET = "********";

	/** The built-in catalog covering every channel notify4j ships. */
	static ChannelCatalog standard() {
		return StandardChannelCatalog.INSTANCE;
	}

	/** Every supported channel, in a stable order. */
	List<ChannelDescriptor> catalog();

	/** The descriptor for {@code scheme}, or empty if unknown. */
	Optional<ChannelDescriptor> describe(String scheme);

	/** {@link #buildUrl(String, Map, Set, boolean)} with no tags and {@code https}. */
	String buildUrl(String scheme, Map<String, String> values);

	/**
	 * Assemble a channel URL from field values. notify4j owns all assembly; the result is
	 * accepted by the parser. <strong>Secret-bearing</strong> — never log it. Does not
	 * validate (call {@link #validate} first); throws {@link IllegalArgumentException}
	 * only for an unknown scheme.
	 * @param scheme the channel scheme
	 * @param values field key → value
	 * @param tags optional routing tags appended as {@code ?tags=}
	 * @param cleartextHttp use the {@code +http} transport (discouraged for credential
	 * channels)
	 */
	String buildUrl(String scheme, Map<String, String> values, Set<String> tags, boolean cleartextHttp);

	/**
	 * Rebuild the URL for a {@link #parse parsed} channel — the symmetric inverse of
	 * {@link #parse}, carrying its scheme, values, tags and transport in one argument.
	 * <p>
	 * Because {@code parse} masks secrets, this overload <strong>rejects</strong> any
	 * value still equal to {@link #MASKED_SECRET} (it would otherwise write the mask as
	 * the credential). To recompose an edited channel while keeping a secret the operator
	 * did not touch, use {@link #recompose} instead.
	 * @param channel a channel whose secret values have all been replaced with real
	 * values
	 * @throws IllegalArgumentException if the scheme is unknown or a secret is still
	 * masked
	 */
	String buildUrl(ParsedChannel channel);

	/**
	 * Recompose an edited channel URL, <strong>preserving secrets the operator left
	 * untouched</strong> — the safe counterpart to {@link #parse} for an "edit channel"
	 * screen. Fields present in {@code editedValues} override the original; any field
	 * left as {@link #MASKED_SECRET} (or omitted) keeps its value from {@code priorUrl}.
	 * Tags and the {@code +http}/{@code +https} transport are carried over from
	 * {@code priorUrl}. The real secret never round-trips through the UI.
	 * <p>
	 * <strong>Secret-bearing</strong> result — never log it.
	 * @param priorUrl the existing channel URL being edited
	 * @param editedValues field key → new value; masked/omitted secrets are kept
	 * @throws IllegalArgumentException if {@code priorUrl} is blank or its scheme unknown
	 */
	String recompose(String priorUrl, Map<String, String> editedValues);

	/**
	 * Check {@code values} against the channel's required fields. Empty list = valid.
	 * Messages reference field keys only, never values.
	 */
	List<ChannelValidationError> validate(String scheme, Map<String, String> values);

	/**
	 * Decompose an existing channel URL back into fields for editing. Secret field values
	 * are returned {@link #MASKED_SECRET masked}.
	 * @throws IllegalArgumentException for an unknown/blank scheme
	 */
	ParsedChannel parse(String url);

	/**
	 * Redact a channel URL to a safe {@code scheme://host/…} form for display/logging,
	 * using the same redactor the delivery path uses.
	 */
	String redact(String url);

}
