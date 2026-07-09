package org.alexmond.notify4j.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Spring Boot entry point for the {@code notify4j} command-line sender.
 * <p>
 * Boots the Spring context (so the notify4j starter's config binding and beans are
 * available), then hands the arguments to the Picocli {@link Notify4jCommand}. The
 * process exit code reported to the shell is Picocli's execution result — {@code 0} when
 * every targeted channel accepted the message, non-zero otherwise — so the CLI is usable
 * as a build step that fails the job on a delivery failure.
 */
@SpringBootApplication
public class Notify4jCliApplication implements CommandLineRunner, ExitCodeGenerator {

	private final IFactory factory;

	private final Notify4jCommand root;

	private int exitCode;

	public Notify4jCliApplication(IFactory factory, Notify4jCommand root) {
		this.factory = factory;
		this.root = root;
	}

	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(Notify4jCliApplication.class, args)));
	}

	@Override
	public void run(String... args) {
		this.exitCode = new CommandLine(this.root, this.factory).setUsageHelpWidth(110).execute(args);
	}

	@Override
	public int getExitCode() {
		return this.exitCode;
	}

}
