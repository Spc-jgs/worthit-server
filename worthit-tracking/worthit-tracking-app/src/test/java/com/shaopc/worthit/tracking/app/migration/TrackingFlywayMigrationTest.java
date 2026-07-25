package com.shaopc.worthit.tracking.app.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class TrackingFlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void migratesVersionOneOnEmptyMysql84Database() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).hasToString("1");
        assertThat(tableExists("trk_item")).isTrue();
        assertThat(sourceWishUniqueIndexColumnCount()).isEqualTo(1);
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = openConnection();
                ResultSet tables = connection.getMetaData().getTables(
                        MYSQL.getDatabaseName(), null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private int sourceWishUniqueIndexColumnCount() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'trk_item'
                  AND index_name = 'uk_item_source_wish'
                  AND column_name = 'source_wish_id'
                """;
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
