package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpClientConfigTest {

	@Test
	void ofAppliesConnectTimeoutAndCarriesRequestTimeout() {
		var cfg = HttpClientConfig.of(Duration.ofSeconds(3), Duration.ofSeconds(7));
		assertThat(cfg.client().connectTimeout()).contains(Duration.ofSeconds(3));
		assertThat(cfg.requestTimeout()).isEqualTo(Duration.ofSeconds(7));
		assertThat(cfg.maxAttempts()).isEqualTo(1); // two-arg of() = no retry
	}

	@Test
	void ofCarriesRetryPolicy() {
		var cfg = HttpClientConfig.of(Duration.ofSeconds(3), Duration.ofSeconds(7), 4, Duration.ofMillis(250));
		assertThat(cfg.maxAttempts()).isEqualTo(4);
		assertThat(cfg.retryBackoff()).isEqualTo(Duration.ofMillis(250));
	}

	@Test
	void maxAttemptsFloorsAtOne() {
		assertThat(HttpClientConfig.of(Duration.ofSeconds(1), Duration.ofSeconds(1), 0, Duration.ofMillis(1))
			.maxAttempts()).isEqualTo(1);
	}

	@Test
	void defaultsAreSharedTenSecondsNoRetry() {
		assertThat(HttpClientConfig.defaults()).isSameAs(HttpClientConfig.defaults());
		assertThat(HttpClientConfig.defaults().requestTimeout()).isEqualTo(Duration.ofSeconds(10));
		assertThat(HttpClientConfig.defaults().client().connectTimeout()).contains(Duration.ofSeconds(10));
		assertThat(HttpClientConfig.defaults().maxAttempts()).isEqualTo(1);
	}

}
