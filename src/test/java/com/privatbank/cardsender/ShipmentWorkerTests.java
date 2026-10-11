package com.privatbank.cardsender;

import com.privatbank.cardsender.client.WaybillGateway;
import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.domain.CardTypes;
import com.privatbank.cardsender.repository.ShipmentRepository;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.ShipmentDispatcher;
import com.privatbank.cardsender.service.ShipmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "ORACLE_TEST_URL", matches = ".+")
class ShipmentWorkerTests extends OracleIntegrationTest {
    @Autowired
    JdbcTemplate db;
    @Autowired
    CardService cards;
    @Autowired
    ShipmentService shipments;
    @Autowired
    ShipmentRepository repository;

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM shipment_orders");
        db.update("DELETE FROM card_orders");
    }

    @Test
    void recordsSuccessRejectionAndUncertaintyWithoutResending() {
        for (var result : new DeliveryResult[]{DeliveryResult.accepted("20400000000001"),
                DeliveryResult.rejected(), DeliveryResult.uncertain()}) {
            var shipment = createShipment();
            var calls = new AtomicInteger();
            WaybillGateway gateway = recipient -> {
                calls.incrementAndGet();
                // This query uses another connection: the claim must already have committed.
                assertEquals("PROCESSING", db.queryForObject("SELECT status FROM shipment_orders WHERE id=?",
                        String.class, shipment.id()));
                return result;
            };
            var dispatcher = new ShipmentDispatcher(repository, gateway);
            dispatcher.dispatchPending(20);
            dispatcher.dispatchPending(20);
            assertEquals(1, calls.get());
            var saved = repository.findById(shipment.id()).orElseThrow();
            assertEquals(switch (result.outcome()) {
                case ACCEPTED -> ShipmentOrder.Status.SENT;
                case REJECTED -> ShipmentOrder.Status.FAILED;
                case UNCERTAIN -> ShipmentOrder.Status.PROCESSING;
            }, saved.status());
            assertEquals(result.waybill(), saved.waybill());
            assertEquals("SHIPMENT_WORKER", saved.audit().updatedBy());
        }
    }

    @Test
    void competingWorkersSubmitOnlyOnce() throws Exception {
        var shipment = createShipment();
        var calls = new AtomicInteger();
        var start = new CountDownLatch(1);
        var dispatcher = new ShipmentDispatcher(repository, recipient -> {
            calls.incrementAndGet();
            return DeliveryResult.accepted("20400000000002");
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                dispatcher.dispatchPending(20);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                dispatcher.dispatchPending(20);
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
            assertEquals(ShipmentOrder.Status.SENT, repository.findById(shipment.id()).orElseThrow().status());
        } finally {
            executor.shutdownNow();
        }
    }

    private ShipmentOrder createShipment() {
        var card = cards.create(new CreateCardInput("Test", "User", CardTypes.GOLD), new Operator("tester"));
        cards.generatePending(new Operator("generator"));
        return shipments.create(cards.getOrder(card.id()).cardNumber(),
                new Recipient("Тест", "Клієнт", "380671234567", "Київ", "1"), new Operator("tester"));
    }
}
