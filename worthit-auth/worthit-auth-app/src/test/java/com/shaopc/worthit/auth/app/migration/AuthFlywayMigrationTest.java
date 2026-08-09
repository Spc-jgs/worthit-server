package com.shaopc.worthit.auth.app.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AuthFlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void migratesLatestAuthSchemaOnEmptyMysql84Database() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).hasToString("3");
        assertThat(tableExists("auth_user")).isTrue();
        assertThat(tableExists("auth_password_credential")).isTrue();
        assertThat(tableExists("auth_idempotency_record")).isTrue();
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                ResultSet tables = connection.getMetaData().getTables(
                        MYSQL.getDatabaseName(), null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }
}
