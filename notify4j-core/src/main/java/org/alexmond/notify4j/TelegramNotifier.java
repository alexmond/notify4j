package org.alexmond.notify4j;

import java.util.List;
import java.util.function.Function;

/**
 * Posts to the Telegram Bot API ({@code POST {baseUrl}/bot{token}/sendMessage} with
 * {@code {"chat_id":…,"text":…}}). {@code baseUrl} is configurable for testing.
 */
public class TelegramNotifier<E> extends AbstractHttpNotifier<E> {

	private final String chatId;

	public TelegramNotifier(String baseUrl, String token, String chatId, Function<E, Object> idFn,
			Function<E, String> statusFn, Function<E, String> messageFn, List<String> ignoreChanges) {
		super(baseUrl + "/bot" + token + "/sendMessage", idFn, statusFn, messageFn, ignoreChanges);
		this.chatId = chatId;
	}

	@Override
	protected String payload(E event) {
		return "{\"chat_id\":" + jsonString(chatId) + ",\"text\":" + jsonString(messageFn.apply(event)) + "}";
	}

}
