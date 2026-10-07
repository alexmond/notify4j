package org.alexmond.notify4j.cli;

import org.alexmond.notify4j.ChannelCatalog;
import org.alexmond.notify4j.ChannelDescriptor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * {@code notify4j channels} — list every channel scheme notify4j supports, from the
 * built-in {@link ChannelCatalog}, so it stays in sync as channels are added.
 */
@Component
@Command(name = "channels", mixinStandardHelpOptions = true,
		description = "List the available channel schemes and their setup docs.")
public class ChannelsCommand implements Runnable {

	@Override
	public void run() {
		for (ChannelDescriptor descriptor : ChannelCatalog.standard().catalog()) {
			String docs = (descriptor.docsUrl() != null) ? descriptor.docsUrl() : "";
			System.out.printf("  %-14s %-20s %s%n", descriptor.scheme(), descriptor.displayName(), docs);
		}
	}

}
