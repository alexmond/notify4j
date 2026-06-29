package org.alexmond.notify4j;

/**
 * A single configurable field of a channel (e.g. a webhook URL, a bot token, a chat id).
 * Obtained from {@link ChannelCatalog}; not meant to be constructed by consumers.
 *
 * @param key stable machine key, used in the {@code values} maps of
 * {@link ChannelCatalog#buildUrl} / {@link ChannelCatalog#validate} (also a reasonable UI
 * label)
 * @param type how to render/enter the field
 * @param required whether a value must be supplied
 * @param secret whether the value is a credential — mask it in UIs and keep it out of
 * logs; {@link ChannelCatalog#parse} returns it masked
 * @since 1.1.0
 */
public record ChannelField(String key, FieldType type, boolean required, boolean secret) {
}
