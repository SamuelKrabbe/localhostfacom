package com.example.localhostfacom.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    @DisplayName("converts a platform URL that carries a port")
    void convertsAPlatformUrlWithAPort() {
        Optional<DatabaseUrl.JdbcConnection> connection =
                DatabaseUrl.fromPlatformUrl("postgresql://appuser:s3cret@db.internal:5433/localhostfacom");

        assertThat(connection).isPresent();
        assertThat(connection.get().url()).isEqualTo("jdbc:postgresql://db.internal:5433/localhostfacom");
        assertThat(connection.get().username()).isEqualTo("appuser");
        assertThat(connection.get().password()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("defaults to 5432 when the platform omits the port")
    void defaultsThePortWhenItIsMissing() {
        Optional<DatabaseUrl.JdbcConnection> connection =
                DatabaseUrl.fromPlatformUrl("postgres://appuser:s3cret@dpg-abc123/localhostfacom");

        assertThat(connection).isPresent();
        assertThat(connection.get().url()).isEqualTo("jdbc:postgresql://dpg-abc123:5432/localhostfacom");
    }

    @Test
    @DisplayName("keeps query parameters such as sslmode")
    void keepsQueryParameters() {
        Optional<DatabaseUrl.JdbcConnection> connection =
                DatabaseUrl.fromPlatformUrl("postgresql://u:p@host:5432/db?sslmode=require");

        assertThat(connection).isPresent();
        assertThat(connection.get().url()).isEqualTo("jdbc:postgresql://host:5432/db?sslmode=require");
    }

    @Test
    @DisplayName("decodes a percent-encoded password")
    void decodesAPercentEncodedPassword() {
        Optional<DatabaseUrl.JdbcConnection> connection =
                DatabaseUrl.fromPlatformUrl("postgresql://user:p%40ss%2Fword@host:5432/db");

        assertThat(connection).isPresent();
        assertThat(connection.get().password()).isEqualTo("p@ss/word");
    }

    @Test
    @DisplayName("reports no credentials when the URL carries none")
    void reportsNoCredentialsWhenTheUrlCarriesNone() {
        Optional<DatabaseUrl.JdbcConnection> connection =
                DatabaseUrl.fromPlatformUrl("postgresql://host:5432/db");

        assertThat(connection).isPresent();
        assertThat(connection.get().username()).isNull();
        assertThat(connection.get().password()).isNull();
    }

    @Test
    @DisplayName("leaves a JDBC URL alone")
    void leavesAJdbcUrlAlone() {
        assertThat(DatabaseUrl.fromPlatformUrl("jdbc:postgresql://host:5432/db")).isEmpty();
    }

    @Test
    @DisplayName("ignores a missing or blank value")
    void ignoresAMissingOrBlankValue() {
        assertThat(DatabaseUrl.fromPlatformUrl(null)).isEmpty();
        assertThat(DatabaseUrl.fromPlatformUrl("   ")).isEmpty();
    }

    @Test
    @DisplayName("ignores a value in a scheme it does not understand")
    void ignoresAnUnknownScheme() {
        assertThat(DatabaseUrl.fromPlatformUrl("mysql://user:pass@host/db")).isEmpty();
    }

    @Test
    @DisplayName("converts a Supabase pooler URL, whose user carries the project ref")
    void convertsASupabasePoolerUrl() {
        Optional<DatabaseUrl.JdbcConnection> connection = DatabaseUrl.fromPlatformUrl(
                "postgresql://postgres.abcdefghij:s3cret@aws-0-sa-east-1.pooler.supabase.com:5432/postgres");

        assertThat(connection).isPresent();
        assertThat(connection.get().url())
                .isEqualTo("jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres");
        // The dot in the username is part of it, not a separator.
        assertThat(connection.get().username()).isEqualTo("postgres.abcdefghij");
        assertThat(connection.get().password()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("reads the host out of a JDBC URL")
    void readsTheHostOutOfAJdbcUrl() {
        assertThat(DatabaseUrl.hostOf("jdbc:postgresql://db.internal:5432/localhostfacom"))
                .contains("db.internal");
    }

    @Test
    @DisplayName("reads the host out of a platform URL")
    void readsTheHostOutOfAPlatformUrl() {
        assertThat(DatabaseUrl.hostOf("postgres://user:pass@postgres.railway.internal:5432/railway"))
                .contains("postgres.railway.internal");
    }

    @Test
    @DisplayName("reads the host out of a URL with no port")
    void readsTheHostOutOfAUrlWithNoPort() {
        assertThat(DatabaseUrl.hostOf("jdbc:postgresql://dpg-abc123/db")).contains("dpg-abc123");
    }

    @Test
    @DisplayName("has no host to report for a missing or unparseable value")
    void hasNoHostToReportForGarbage() {
        assertThat(DatabaseUrl.hostOf(null)).isEmpty();
        assertThat(DatabaseUrl.hostOf("")).isEmpty();
        assertThat(DatabaseUrl.hostOf("not a url")).isEmpty();
    }
}
