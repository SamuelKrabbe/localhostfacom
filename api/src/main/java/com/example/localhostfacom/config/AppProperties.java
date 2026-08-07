package com.example.localhostfacom.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        BootstrapAdmin bootstrapAdmin,
        Payments payments,
        Storage storage) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, Duration ttl) {}

    public record BootstrapAdmin(String email, String password) {}

    public record Payments(
            String activeProvider,
            Duration orderTtl,
            MercadoPago mercadoPago,
            Fake fake) {

        public record MercadoPago(String accessToken, String webhookSecret, String baseUrl) {}

        public record Fake(Duration autoConfirmAfter) {}
    }

    public record Storage(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            String publicBaseUrl,
            boolean pathStyle) {}
}
