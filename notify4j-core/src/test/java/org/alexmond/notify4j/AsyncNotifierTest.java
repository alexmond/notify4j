package org.alexmond.notify4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AsyncNotifierTest {

	@Test
	void dispatchesToExecutorAndDelegate() {
		var seen = new AtomicReference<String>();
		// inline executor runs the task synchronously — asserts wiring deterministically
		var async = new AsyncNotifier<String>(seen::set, Runnable::run);

		async.notify("hello");

		assertThat(seen.get()).isEqualTo("hello");
	}

	@Test
	void isolatesDelegateFailure() {
		var async = new AsyncNotifier<String>((e) -> {
			throw new IllegalStateException("boom");
		}, Runnable::run);

		// AsyncNotifier must never propagate a delegate failure to the caller.
		assertThatCode(() -> async.notify("x")).doesNotThrowAnyException();
	}

	@Test
	void recordsDropAndSwallowsWhenPoolRejects() {
		var dropped = new AtomicReference<String>();
		NotificationMetrics metrics = new NotificationMetrics() {
			@Override
			public void recordSent(String channel) {
			}

			@Override
			public void recordFailed(String channel) {
			}

			@Override
			public void recordSuppressed(String channel) {
			}

			@Override
			public void recordDropped(String channel) {
				dropped.set(channel);
			}
		};
		// an executor that always rejects, standing in for a full bounded queue
		Executor rejecting = (r) -> {
			throw new RejectedExecutionException("queue full");
		};
		var delivered = new AtomicReference<String>();
		var async = new AsyncNotifier<String>(delivered::set, rejecting, metrics, "slack");

		// the drop must be swallowed (never breaks the caller) and recorded
		assertThatCode(() -> async.notify("x")).doesNotThrowAnyException();
		assertThat(delivered.get()).isNull();
		assertThat(dropped.get()).isEqualTo("slack");
	}

	@Test
	void deliversOffTheCallerThread() throws Exception {
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			var deliveredOn = new AtomicReference<String>();
			var latch = new CountDownLatch(1);
			var async = new AsyncNotifier<String>((e) -> {
				deliveredOn.set(Thread.currentThread().getName());
				latch.countDown();
			}, pool);

			String caller = Thread.currentThread().getName();
			async.notify("e");

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(deliveredOn.get()).isNotEqualTo(caller);
		}
		finally {
			pool.shutdownNow();
		}
	}

}
