package com.example.localhostfacom;

import com.example.localhostfacom.config.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalhostfacomApplication {

	public static void main(String[] args) {
		applyPlatformDatabaseUrl();
		SpringApplication.run(LocalhostfacomApplication.class, args);
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
