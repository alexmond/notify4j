package org.alexmond.notify4j.cli;

import java.util.concurrent.Callable;

import org.alexmond.notify4j.ChannelCatalog;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code notify4j validate URL} — check that a channel URL names a known scheme and
 * parses. Prints the redacted (secret-masked) URL on success. Exit {@code 0} valid,
 * {@code 1} not.
 */
@Component
@Command(name = "validate", mixinStandardHelpOptions = true,
		description = "Check that a channel URL is recognized and parseable (prints it redacted).")
public class ValidateCommand implements Callable<Integer> {

	@Parameters(index = "0", paramLabel = "URL", description = "The channel URL to validate.")
	private String url;

	@Override
	public Integer call() {
		ChannelCatalog catalog = ChannelCatalog.standard();
		if (catalog.tryParse(this.url).isPresent()) {
			System.out.println("valid: " + catalog.redact(this.url));
			return 0;
		}
		System.err.println("invalid: unrecognized scheme or unparseable URL");
		return 1;
	}

}
