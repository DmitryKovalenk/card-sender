package com.privatbank.cardsender;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.dto.CardTypes;
import com.privatbank.cardsender.repository.CardOrderRepository;
import com.privatbank.cardsender.repository.CardTypeRepository;
import com.privatbank.cardsender.repository.ShipmentRepository;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.ShipmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ORACLE_TEST_URL", matches = ".+")
class JpaPersistenceTests extends OracleIntegrationTest {
    @Autowired
    CardService cards;
    @Autowired
    ShipmentService shipments;
    @Autowired
    CardOrderRepository orders;
    @Autowired
    CardTypeRepository types;
    @Autowired
    ShipmentRepository shipmentRepository;
    @Autowired
    EntityManager entityManager;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    JdbcTemplate db;

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM shipment_orders");
        db.update("DELETE FROM card_orders");
    }

    @Test
    void procedureRefreshesSubsequentReadsAndRollsBackWithCaller() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            var order = cards.create(new CreateCardInput("Test", "User", CardTypes.ELITE), new Operator("creator"));
            assertNotEquals(0, order.id());
            assertNotNull(order.audit().createdAt());
            assertNotNull(order.audit().updatedAt());
            assertTrue(entityManager.contains(order));
            String number = cards.generateNumber(order.id(), new Operator("generator"));
            assertTrue(number.startsWith("000000"));
            var reloaded = cards.getOrder(order.id());
            assertEquals(number, reloaded.cardNumber());
            assertEquals(CardOrder.Status.GENERATED, reloaded.status());
            assertNotNull(reloaded.generatedAt());
            assertEquals("generator", reloaded.audit().updatedBy());
            assertEquals(number, cards.generateNumber(order.id(), new Operator("again")));
            transaction.setRollbackOnly();
        });
        assertEquals(0, orders.count());
    }

    @Test
    void generationSkipsRowsLockedByAnotherTransaction() throws Exception {
        var locked = cards.create(new CreateCardInput("Locked", "User", CardTypes.GOLD), new Operator("tester"));
        var available = cards.create(new CreateCardInput("Available", "User", CardTypes.GOLD), new Operator("tester"));
        var ready = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var held = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                entityManager.find(CardOrder.class, locked.id(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                ready.countDown();
                try {
                    assertTrue(release.await(20, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            assertEquals(1, cards.generatePending(new Operator("generator")));
            assertNotNull(cards.getOrder(available.id()).cardNumber());
            assertNull(cards.getOrder(locked.id()).cardNumber());
            release.countDown();
            held.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void claimCommitsIndependentlyAndCompletionCannotBeRepeated() {
        var card = cards.create(new CreateCardInput("Test", "User", CardTypes.GOLD), new Operator("tester"));
        String number = cards.generateNumber(card.id(), new Operator("generator"));
        var shipment = shipments.create(number,
                new Recipient("Тест", "Клієнт", "380671234567", "Київ", "1"), new Operator("tester"));
        assertNotNull(shipment.audit().createdAt());
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertTrue(shipmentRepository.claim(shipment.id()));
            transaction.setRollbackOnly();
        });
        assertEquals(ShipmentOrder.Status.PROCESSING, shipmentRepository.findById(shipment.id()).orElseThrow().status());
        assertFalse(shipmentRepository.claim(shipment.id()));
        shipmentRepository.complete(shipment.id(), ShipmentOrder.Status.SENT, "20400000000009");
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> shipmentRepository.complete(shipment.id(), ShipmentOrder.Status.FAILED, null));
        assertEquals("20400000000009", shipmentRepository.findById(shipment.id()).orElseThrow().waybill());
    }

    @Test
    void binLookupLocksTheRowUntilTransactionCompletes() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(OracleTestDatabase.url(),
                OracleTestDatabase.username(), OracleTestDatabase.password())) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                    assertTrue(types.findByBinForUpdate("444444").isPresent());
                    var failure = assertThrows(java.sql.SQLException.class, () -> statement.executeQuery(
                            "SELECT id FROM card_types WHERE bin='444444' FOR UPDATE NOWAIT"));
                    assertEquals(54, failure.getErrorCode());
                });
                try (var rows = statement.executeQuery(
                        "SELECT id FROM card_types WHERE bin='444444' FOR UPDATE NOWAIT")) {
                    assertTrue(rows.next());
                }
            } finally {
                connection.rollback();
            }
        }
    }


    @Test
    void recipientRecordSurvivesPersistenceContextReload() {
        var recipient = new Recipient("Дмитро", "Коваленко", "380671234567", "Київ", "31/2");
        var card = cards.create(new CreateCardInput("Test", "User", CardTypes.GOLD), new Operator("tester"));
        var shipment = shipments.create(cards.generateNumber(card.id(), new Operator("generator")),
                recipient, new Operator("tester"));
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            entityManager.clear();
            var reloaded = shipmentRepository.findById(shipment.id()).orElseThrow();
            assertEquals(recipient, reloaded.recipient());
            assertEquals("tester", reloaded.audit().createdBy());
            assertNotNull(reloaded.audit().createdAt());
        });
    }
}
