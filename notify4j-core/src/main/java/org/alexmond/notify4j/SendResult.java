package org.alexmond.notify4j;

/**
 * The outcome of a {@link Notifications#sendOnce one-shot send}: how many channels were
 * attempted, and how many succeeded vs. failed. Counts only — it names no channel and
 * carries no URL, so it is always safe to log (a channel URL holds secrets).
 *
 * <p>
 * Because {@code sendOnce} delivers synchronously, the counts are final and complete by
 * the time it returns: {@code attempted == sent + failed}. A failing channel is never
 * thrown; inspect {@link #anyFailed()} (e.g. to set a CLI exit code).
 *
 * @param attempted number of channels delivery was attempted on
 * @param sent number that succeeded
 * @param failed number that failed (after any retries) and were swallowed
 * @since 1.1.0
 */
public record SendResult(int attempted, int sent, int failed) {

	/** Whether any channel failed to deliver. */
	public boolean anyFailed() {
		return this.failed > 0;
	}

}
