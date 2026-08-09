package com.shaopc.worthit.tracking.app.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TrackingFlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Test
    void upgradesVersionOneToM3AccountCancellationFenceOnMysql84()
            throws SQLException {
        Flyway versionOne = Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("1")
                .load();

        MigrateResult versionOneResult = versionOne.migrate();
        assertThat(versionOneResult.success).isTrue();
        assertThat(versionOneResult.targetSchemaVersion)
                .hasToString("1");
        assertThat(versionOneResult.migrationsExecuted)
                .isEqualTo(1);
        assertThat(tableExists("trk_item")).isTrue();
        assertThat(tableExists("trk_item_disposal"))
                .isFalse();

        Flyway versionTwo = Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        MigrateResult versionTwoResult = versionTwo.migrate();

        assertThat(versionTwoResult.success).isTrue();
        assertThat(versionTwoResult.targetSchemaVersion)
                .hasToString("3");
        assertThat(versionTwoResult.migrationsExecuted)
                .isEqualTo(2);
        assertThat(tableExists("trk_item_disposal")).isTrue();
        assertThat(tableExists("trk_item_replacement")).isTrue();
        assertThat(tableExists("trk_user_write_fence")).isTrue();
        assertThat(sourceWishUniqueIndexColumnCount()).isEqualTo(1);

        insertDisposal(
                1L,
                101L,
                "RETURNED",
                LocalDate.of(2026, 7, 30),
                "1000.000000",
                null);

        assertThatThrownBy(() -> insertDisposal(
                2L,
                101L,
                "SCRAPPED",
                LocalDate.of(2026, 7, 30),
                "1000.000000",
                null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertDisposal(
                3L,
                102L,
                "SOLD",
                LocalDate.of(2026, 7, 30),
                "1000.000000",
                null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertDisposal(
                4L,
                103L,
                "RETURNED",
                LocalDate.of(2026, 7, 30),
                "-0.000001",
                null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertDisposal(
                5L,
                104L,
                "RETURNED",
                LocalDate.of(2026, 7, 30),
                "1000.000000",
                "800.000000"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertDisposal(
                6L,
                105L,
                "DONATED",
                LocalDate.of(2026, 7, 30),
                "1000.000000",
                null))
                .isInstanceOf(SQLException.class);
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

    private void insertDisposal(
            long id,
            long itemId,
            String type,
            LocalDate date,
            String purchasePriceSnapshot,
            String saleAmount) throws SQLException {
        String sql = """
                INSERT INTO trk_item_disposal (
                    id, user_id, item_id, disposal_type,
                    disposal_date, purchase_price_snapshot,
                    sale_amount, remark, create_time, update_time
                ) VALUES (?, 1001, ?, ?, ?, ?, ?, NULL, NOW(3), NOW(3))
                """;
        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, itemId);
            statement.setString(3, type);
            statement.setObject(4, date);
            statement.setBigDecimal(
                    5, new BigDecimal(purchasePriceSnapshot));
            if (saleAmount == null) {
                statement.setNull(6, Types.DECIMAL);
            } else {
                statement.setBigDecimal(
                        6, new BigDecimal(saleAmount));
            }
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
