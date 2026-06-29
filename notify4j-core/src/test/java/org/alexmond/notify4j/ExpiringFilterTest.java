package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Expiry is driven by an injectable {@link Clock}, so active→expired is deterministic.
 */
class ExpiringFilterTest {

	private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void mutesViaItsPredicate() {
		var filter = new ExpiringFilter<String>("mute-x", (e) -> e.equals("x"), null);
		assertThat(filter.mutes("x")).isTrue();
		assertThat(filter.mutes("y")).isFalse();
		assertThat(filter.id()).isEqualTo("mute-x");
	}

	@Test
	void isNotExpiredBeforeTheInstant() {
		Instant expiry = T0.plusSeconds(60);
		var filter = new ExpiringFilter<String>("m", (e) -> true, expiry, Clock.fixed(T0, ZoneOffset.UTC));
		assertThat(filter.isExpired()).isFalse();
		assertThat(filter.expiresAt()).isEqualTo(expiry);
	}

	@Test
	void isExpiredAfterTheInstant() {
		Instant expiry = T0.plusSeconds(60);
		var filter = new ExpiringFilter<String>("m", (e) -> true, expiry,
				Clock.fixed(T0.plusSeconds(61), ZoneOffset.UTC));
		assertThat(filter.isExpired()).isTrue();
	}

	@Test
	void neverExpiresWhenExpiresAtIsNull() {
		var filter = new ExpiringFilter<String>("m", (e) -> true, null,
				Clock.fixed(T0.plusSeconds(99999), ZoneOffset.UTC));
		assertThat(filter.isExpired()).isFalse();
	}

}
