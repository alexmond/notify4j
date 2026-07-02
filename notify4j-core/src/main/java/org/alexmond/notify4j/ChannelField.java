package org.alexmond.notify4j;

/**
 * A single configurable field of a channel (e.g. a webhook URL, a bot token, a chat id),
 * carrying both its machine shape and the strings a UI needs to render it. Obtained from
 * {@link ChannelCatalog}; not meant to be constructed by consumers.
 *
 * @param key stable machine key, used in the {@code values} maps of
 * {@link ChannelCatalog#buildUrl} / {@link ChannelCatalog#validate}
 * @param type how to render/enter the field
 * @param required whether a value must be supplied
 * @param secret whether the value is a credential — mask it in UIs and keep it out of
 * logs; {@link ChannelCatalog#parse} returns it masked
 * @param label a short human-readable label for the input (e.g. {@code "Webhook URL"})
 * @param description one-line help text describing what to enter, or {@code null}
 * @param example a non-secret example value to show as a placeholder, or {@code null}
 * (never populated for {@link #secret} fields)
 * @since 1.1.0
 */
public record ChannelField(String key, FieldType type, boolean required, boolean secret, String label,
		String description, String example) {
}
