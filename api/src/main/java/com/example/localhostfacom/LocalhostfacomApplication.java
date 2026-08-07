package com.example.localhostfacom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalhostfacomApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocalhostfacomApplication.class, args);
	}

}
