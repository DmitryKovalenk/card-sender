package com.privatbank.cardsender;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Run against an empty, disposable Oracle schema using ORACLE_TEST_* variables.
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_TEST_URL", matches = ".+")
class OracleMigrationTests {
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("ORACLE_TEST_URL"),
                System.getenv("ORACLE_TEST_USERNAME"), System.getenv("ORACLE_TEST_PASSWORD"));
    }

    @BeforeAll
    static void migrate() throws Exception {
        OracleTestDatabase.prepare();
        var flyway = OracleTestDatabase.flyway();
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void schemaAndSeedsAreValid() throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM user_errors"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM user_objects WHERE object_name='GENERATE_CARD_NUMBER' AND status='VALID'"));
            var expected = Map.of("444444", "Золота", "555555", "Срібна", "666666", "Дерев’яна",
                    "000000", "Елітна та найкрутіша в світі картка мрії");
            assertEquals(4, scalar(connection, "SELECT COUNT(*) FROM card_types"));
            try (var rows = statement.executeQuery("SELECT name, bin, is_active, created_by FROM card_types")) {
                while (rows.next()) {
                    assertEquals(expected.get(rows.getString("bin")), rows.getString("name"));
                    assertEquals(1, rows.getInt("is_active"));
                    assertEquals("FLYWAY", rows.getString("created_by"));
                }
            }
        }
    }

    @Test
    void procedureGeneratesUniqueLuhnNumbersAndRetriesCollisions() throws Exception {
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long first = insertOrder(connection, "000000");
                long second = insertOrder(connection, "000000");
                assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM card_orders WHERE card_number IS NULL"));
                seedRandom(connection);
                String number = generate(connection, first);
                assertTrue(number.startsWith("000000"));
                assertTrue(validLuhn(number));
                assertEquals(number, generate(connection, first));

                // Replaying the PRNG forces the next call's first candidate to collide.
                seedRandom(connection);
                String other = generate(connection, second);
                assertNotEquals(number, other);
                assertTrue(validLuhn(other));

                var numbers = new HashSet<String>();
                for (int i = 0; i < 100; i++) {
                    String generated = generate(connection, insertOrder(connection, "444444"));
                    assertTrue(generated.startsWith("444444"));
                    assertTrue(validLuhn(generated));
                    assertTrue(numbers.add(generated));
                }
                assertEquals(102, scalar(connection, "SELECT COUNT(*) FROM card_orders WHERE status='GENERATED' AND generated_at IS NOT NULL AND updated_by='oracle-test'"));
                try (var update = connection.prepareStatement("UPDATE card_orders SET card_number=? WHERE id=?")) {
                    update.setString(1, number);
                    update.setLong(2, second);
                    assertEquals(1, assertThrows(SQLException.class, update::executeUpdate).getErrorCode());
                }
            } finally {
                connection.rollback();
            }
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM card_orders"),
                    "The procedure must not commit the caller's transaction");
        }
    }

    @Test
    void shipmentForeignKeyAndUniquenessAreEnforced() throws Exception {
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long order = insertOrder(connection, "555555");
                generate(connection, order);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO shipment_orders(card_order_id, recipient_first_name, recipient_last_name,
                            recipient_phone, recipient_city, warehouse_number, created_by, updated_by)
                        VALUES (?, 'Тест', 'Клієнт', '380671234567', 'Київ', '1', 'oracle-test', 'oracle-test')
                        """)) {
                    insert.setLong(1, -1);
                    assertEquals(2291, assertThrows(SQLException.class, insert::executeUpdate).getErrorCode());
                    insert.setLong(1, order);
                    assertEquals(1, insert.executeUpdate());
                    assertEquals(1, assertThrows(SQLException.class, insert::executeUpdate).getErrorCode());
                }
                try (var statement = connection.createStatement()) {
                    assertEquals(2290, assertThrows(SQLException.class,
                            () -> statement.executeUpdate("UPDATE shipment_orders SET status='SENT'"))
                            .getErrorCode());
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static long insertOrder(Connection connection, String bin) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO card_orders(card_type_id, first_name, last_name, created_by, updated_by)
                VALUES ((SELECT id FROM card_types WHERE bin=?), 'Test', 'User', 'oracle-test', 'oracle-test')
                """, new String[]{"ID"})) {
            insert.setString(1, bin);
            insert.executeUpdate();
            try (var keys = insert.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static String generate(Connection connection, long order) throws SQLException {
        try (var call = connection.prepareCall("{call generate_card_number(?, ?, ?)}")) {
            call.setLong(1, order);
            call.setString(2, "oracle-test");
            call.registerOutParameter(3, Types.VARCHAR);
            call.execute();
            return call.getString(3);
        }
    }

    private static void seedRandom(Connection connection) throws SQLException {
        try (var call = connection.prepareCall("BEGIN DBMS_RANDOM.SEED(123456); END;")) {
            call.execute();
        }
    }

    private static int scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static boolean validLuhn(String number) {
        if (!number.matches("[0-9]{16}")) return false;
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
