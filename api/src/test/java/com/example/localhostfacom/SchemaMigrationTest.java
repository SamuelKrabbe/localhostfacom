package com.example.localhostfacom;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsCreateEveryTable() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }

        assertThat(tables).contains(
                "image", "admin", "product", "orders", "order_item",
                "expense", "settings", "webhook_event");
    }

    @Test
    void settingsRowIsSeededWithAPositiveGoal() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.createStatement()
                     .executeQuery("SELECT goal_target FROM settings WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBigDecimal("goal_target")).isPositive();
        }
    }
}
