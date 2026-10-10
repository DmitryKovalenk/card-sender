package com.privatbank.cardsender;

import org.flywaydb.core.Flyway;

import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Initializes one empty Oracle test schema per JVM; never cleans an existing schema.
 */
final class OracleTestDatabase {
    private static boolean prepared;

    private OracleTestDatabase() {
    }

    static String url() {
        return System.getenv("ORACLE_TEST_URL");
    }

    static String username() {
        return System.getenv("ORACLE_TEST_USERNAME");
    }

    static String password() {
        return System.getenv("ORACLE_TEST_PASSWORD");
    }

    static synchronized void prepare() {
        if (prepared) {
            return;
        }
        try (var connection = DriverManager.getConnection(url(), username(), password());
             var statement = connection.createStatement();
             var tables = statement.executeQuery("SELECT COUNT(*) FROM user_tables")) {
            tables.next();
            if (tables.getInt(1) != 0) {
                throw new IllegalStateException("Oracle tests require an empty, disposable schema");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot connect to the Oracle test schema", exception);
        }
        var flyway = flyway();
        if (flyway.migrate().migrationsExecuted != 5) {
            throw new IllegalStateException("Expected all five migrations on a fresh Oracle schema");
        }
        prepared = true;
    }

    static Flyway flyway() {
        return Flyway.configure().dataSource(url(), username(), password())
                .locations("classpath:db/migration").validateMigrationNaming(true).cleanDisabled(true).load();
    }
}
