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
 * real credential — so the value never round-trips through the UI. To recompose an edited
 * channel while keeping a secret the operator left untouched, pass the edits back through
 * {@link ChannelCatalog#recompose} with the original URL; it fills masked fields from the
 * original rather than writing the mask as the credential.
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
