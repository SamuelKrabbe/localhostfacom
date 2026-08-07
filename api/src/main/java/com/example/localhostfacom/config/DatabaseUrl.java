package com.example.localhostfacom.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Render, Railway, Heroku and Fly all publish DATABASE_URL as a libpq URI
 * ({@code postgres://user:pass@host:port/db}). Spring Boot only accepts a JDBC URL and
 * fails at startup with {@code 'url' must start with "jdbc"}, so the platform form is
 * translated here before the context is built.
 */
public final class DatabaseUrl {

    private static final int DEFAULT_PORT = 5432;

    /** Credentials are null when the URL carries none; the caller keeps its own defaults then. */
    public record JdbcConnection(String url, String username, String password) {}

    private DatabaseUrl() {}

    public static Optional<JdbcConnection> fromPlatformUrl(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
            return Optional.empty();
        }

        int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();
        StringBuilder url = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost())
                .append(':')
                .append(port)
                .append(uri.getPath());
        if (uri.getQuery() != null) {
            url.append('?').append(uri.getRawQuery());
        }

        String rawUserInfo = uri.getRawUserInfo();
        if (rawUserInfo == null) {
            return Optional.of(new JdbcConnection(url.toString(), null, null));
        }

        int separator = rawUserInfo.indexOf(':');
        String username = decode(separator == -1 ? rawUserInfo : rawUserInfo.substring(0, separator));
        String password = separator == -1 ? null : decode(rawUserInfo.substring(separator + 1));
        return Optional.of(new JdbcConnection(url.toString(), username, password));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
