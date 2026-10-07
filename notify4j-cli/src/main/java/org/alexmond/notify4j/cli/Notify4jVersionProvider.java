package org.alexmond.notify4j.cli;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import picocli.CommandLine.IVersionProvider;

/**
 * Supplies {@code notify4j --version} from Spring Boot build-info (stamped into
 * {@code META-INF/build-info.properties} by the {@code spring-boot:build-info} goal), so
 * the reported version is the real build, not a hardcoded literal. Falls back to
 * {@code dev} when running without build-info (e.g. straight from the IDE).
 */
@Component
public class Notify4jVersionProvider implements IVersionProvider {

	private final ObjectProvider<BuildProperties> build;

	public Notify4jVersionProvider(ObjectProvider<BuildProperties> build) {
		this.build = build;
	}

	@Override
	public String[] getVersion() {
		BuildProperties properties = this.build.getIfAvailable();
		String version = (properties != null) ? properties.getVersion() : "dev";
		return new String[] { "notify4j-cli " + version };
	}

}
