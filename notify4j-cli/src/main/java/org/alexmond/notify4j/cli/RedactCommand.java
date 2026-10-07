package org.alexmond.notify4j.cli;

import java.util.concurrent.Callable;

import org.alexmond.notify4j.ChannelCatalog;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code notify4j redact URL} — print a channel URL with its secret masked, safe to paste
 * into logs or an issue. Uses the same redactor the delivery path uses.
 */
@Component
@Command(name = "redact", mixinStandardHelpOptions = true,
		description = "Print a channel URL with its secret masked, safe for logs.")
public class RedactCommand implements Callable<Integer> {

	@Parameters(index = "0", paramLabel = "URL", description = "The channel URL to redact.")
	private String url;

	@Override
	public Integer call() {
		System.out.println(ChannelCatalog.standard().redact(this.url));
		return 0;
	}

}
