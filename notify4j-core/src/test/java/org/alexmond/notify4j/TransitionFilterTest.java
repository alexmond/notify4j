package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;

class TransitionFilterTest {

	@Test
	void firesOnRealTransitionsOnly() {
		TransitionFilter f = new TransitionFilter();
		assertThat(f.allow("e", "RUNNING")).isTrue(); // UNKNOWN -> RUNNING
		assertThat(f.allow("e", "RUNNING")).isFalse(); // no change
		assertThat(f.allow("e", "FAILED")).isTrue(); // RUNNING -> FAILED
	}

	@Test
	void respectsIgnoreChanges() {
		TransitionFilter f = new TransitionFilter(List.of("*:RUNNING"));
		assertThat(f.allow("e", "RUNNING")).isFalse(); // ignored
		assertThat(f.allow("e", "FAILED")).isTrue();
	}

	@Test
	void nullIdIsDeliveredAndNullStatusDoesNotThrow() {
		TransitionFilter f = new TransitionFilter();
		assertThat(f.allow(null, "FAILED")).isTrue(); // no identity to dedupe on
		assertThatCode(() -> f.allow("e", null)).doesNotThrowAnyException(); // no
																				// ConcurrentHashMap
																				// NPE
	}

}
