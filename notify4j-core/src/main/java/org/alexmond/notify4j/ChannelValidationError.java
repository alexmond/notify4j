package org.alexmond.notify4j;

/**
 * A problem found by {@link ChannelCatalog#validate}. The message references the field
 * <em>key</em>, never a supplied value (so it can't leak a secret).
 *
 * @param fieldKey the offending field's key, or {@code null} for a whole-config / unknown
 * scheme error
 * @param code a stable machine code ({@code "required"}, {@code "unknown_scheme"})
 * @param message a short human-readable description (no field values)
 * @since 1.1.0
 */
public record ChannelValidationError(String fieldKey, String code, String message) {
}
