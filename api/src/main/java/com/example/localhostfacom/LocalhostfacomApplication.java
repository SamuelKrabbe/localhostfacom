package com.example.localhostfacom;

import com.example.localhostfacom.config.DatabaseUrl;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalhostfacomApplication {

	public static void main(String[] args) {
		applyPlatformDatabaseUrl();
		reportDatabaseTarget();
		SpringApplication.run(LocalhostfacomApplication.class, args);
	}

	/**
	 * A failed connection reports "Network is unreachable" without naming the host or the
	 * address it dialled, which is the one thing needed to tell an IPv6-only private
	 * network apart from a wrong hostname. Printed before the context starts, so it
	 * survives a startup failure. Credentials are never part of this.
	 */
	private static void reportDatabaseTarget() {
		String url = System.getProperty("spring.datasource.url", System.getenv("DATABASE_URL"));
		String host = DatabaseUrl.hostOf(url).orElse(null);
		if (host == null) {
			return;
		}
		try {
			String addresses = Arrays.stream(InetAddress.getAllByName(host))
					.map(InetAddress::getHostAddress)
					.collect(Collectors.joining(", "));
			System.out.println("Database host " + host + " resolves to: " + addresses);
		} catch (UnknownHostException exception) {
			System.out.println("Database host " + host + " does not resolve: " + exception.getMessage());
		}
	}

	/**
	 * Has to run before the context exists, since the DataSource is built during refresh.
	 * A DATABASE_URL that is already a JDBC URL is left for application-prod.yaml to read.
	 */
	private static void applyPlatformDatabaseUrl() {
		DatabaseUrl.fromPlatformUrl(System.getenv("DATABASE_URL")).ifPresent(connection -> {
			System.setProperty("spring.datasource.url", connection.url());
			if (connection.username() != null) {
				System.setProperty("spring.datasource.username", connection.username());
			}
			if (connection.password() != null) {
				System.setProperty("spring.datasource.password", connection.password());
			}
		});
	}

}
