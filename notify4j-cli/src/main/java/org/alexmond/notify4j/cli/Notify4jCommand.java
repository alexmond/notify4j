package org.alexmond.notify4j.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Root {@code notify4j} command. Holds the subcommands; with no subcommand it prints
 * usage.
 */
@Component
@Command(name = "notify4j", mixinStandardHelpOptions = true, versionProvider = Notify4jVersionProvider.class,
		subcommands = { SendCommand.class, ChannelsCommand.class, ValidateCommand.class, RedactCommand.class },
		description = "Send notifications to any notify4j channel URL, from a shell or a CI build.")
public class Notify4jCommand implements Runnable {

	@Spec
	private CommandSpec spec;

	@Override
	public void run() {
		this.spec.commandLine().usage(System.err);
	}

}
