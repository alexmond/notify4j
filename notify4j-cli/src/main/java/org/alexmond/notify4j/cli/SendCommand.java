package org.alexmond.notify4j.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.alexmond.notify4j.Message;
import org.alexmond.notify4j.Notifications;
import org.alexmond.notify4j.SendResult;
import org.alexmond.notify4j.Severity;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code notify4j send} — deliver one ad-hoc message to one or more channel URLs via
 * {@link Notifications#sendOnce}. Sends per-channel so a failure can name the channel by
 * scheme; the aggregate {@link SendResult} is counts-only. Exit code is {@code 0} when
 * all channels accepted the message, {@code 1} if any failed, {@code 2} for a usage
 * error.
 */
@Component
@Command(name = "send", mixinStandardHelpOptions = true,
		description = "Send one notification to one or more channel URLs.")
public class SendCommand implements Callable<Integer> {

	@Option(names = { "-u", "--url" }, paramLabel = "URL",
			description = "Channel URL (repeatable). If omitted, read from $NOTIFY4J_URLS (comma/newline separated).")
	private List<String> urls = new ArrayList<>();

	@Option(names = { "-t", "--title" }, paramLabel = "TITLE",
			description = "Message title (maps to the channel's native title where supported).")
	private String title;

	@Option(names = { "-b", "--body" }, paramLabel = "BODY", description = "Message body. If omitted, read from stdin.")
	private String body;

	@Option(names = { "-s", "--severity" }, paramLabel = "SEV", defaultValue = "INFO",
			description = "Severity: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
	private Severity severity;

	@Option(names = { "-q", "--quiet" }, description = "Suppress per-channel output; only set the exit code.")
	private boolean quiet;

	@Override
	public Integer call() throws Exception {
		List<String> targets = resolveUrls();
		if (targets.isEmpty()) {
			System.err.println("no channel URLs — pass --url one or more times, or set $NOTIFY4J_URLS");
			return 2;
		}
		String text = (this.body != null) ? this.body : readStdin();
		Message message = Message.of(this.title, text, this.severity);
		int sent = 0;
		int failed = 0;
		for (String url : targets) {
			String scheme = url.replaceFirst("[:+].*", "");
			SendResult result = Notifications.sendOnce(List.of(url), message);
			boolean ok = !result.anyFailed();
			if (ok) {
				sent++;
			}
			else {
				failed++;
			}
			if (!this.quiet) {
				System.out.println((ok ? "  ok   " : "  FAIL ") + scheme);
			}
		}
		if (!this.quiet) {
			System.out.println("sent " + sent + "/" + targets.size() + " (failed " + failed + ")");
		}
		return (failed > 0) ? 1 : 0;
	}

	private List<String> resolveUrls() {
		if (!this.urls.isEmpty()) {
			return this.urls.stream().filter((u) -> u != null && !u.isBlank()).collect(Collectors.toList());
		}
		String env = System.getenv("NOTIFY4J_URLS");
		if (env == null || env.isBlank()) {
			return List.of();
		}
		return Arrays.stream(env.split("[,\n]"))
			.map(String::trim)
			.filter((s) -> !s.isEmpty())
			.collect(Collectors.toList());
	}

	private String readStdin() throws java.io.IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		}
	}

}
