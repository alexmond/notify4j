package org.alexmond.notify4j;

import java.util.Map;
import java.util.Set;

/**
 * An existing channel URL decomposed back into fields, for an "edit channel" screen.
 * Returned by {@link ChannelCatalog#parse}.
 *
 * <p>
 * <strong>Secrets are masked.</strong> Every {@link ChannelField#secret() secret} field
 * that had a value is returned as {@link ChannelCatalog#MASKED_SECRET} rather than the
 * real credential — so the value never round-trips through the UI. The "blank/unchanged =
 * keep existing" pattern: when the operator leaves a masked field untouched, re-derive
 * the URL from the stored original rather than from the masked value.
 *
 * @param scheme the channel scheme
 * @param values field key → value, in descriptor order; secret values are masked, absent
 * optional fields are omitted
 * @param tags the {@code ?tags=} routing tags, if any
 * @param cleartextHttp whether the URL used the {@code +http} (cleartext) transport
 * @since 1.1.0
 */
public record ParsedChannel(String scheme, Map<String, String> values, Set<String> tags, boolean cleartextHttp) {
}
