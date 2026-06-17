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
	}

	@Test
	void defaultsAreSharedAndTenSeconds() {
		assertThat(HttpClientConfig.defaults()).isSameAs(HttpClientConfig.defaults());
		assertThat(HttpClientConfig.defaults().requestTimeout()).isEqualTo(Duration.ofSeconds(10));
		assertThat(HttpClientConfig.defaults().client().connectTimeout()).contains(Duration.ofSeconds(10));
	}

}
