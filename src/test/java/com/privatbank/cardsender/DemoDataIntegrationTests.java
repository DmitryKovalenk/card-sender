package com.privatbank.cardsender;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.dto.CardTypes;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.demo.DemoDataService;
import com.privatbank.cardsender.service.demo.DemoRecipientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

@EnabledIfEnvironmentVariable(named = "ORACLE_TEST_URL", matches = ".+")
class DemoDataIntegrationTests extends OracleIntegrationTest {
    @Autowired
    DemoDataService demo;
    @Autowired
    CardService cards;
    @Autowired
    JdbcTemplate db;
    @MockitoSpyBean
    DemoRecipientFactory recipients;

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM shipment_orders");
        db.update("DELETE FROM card_orders");
    }

    @Test
    void createsValidDemoPairsWithoutGeneratingOtherPendingOrders() {
        var existing = cards.create(new CreateCardInput("Existing", "User", CardTypes.GOLD), new Operator("tester"));

        assertEquals(new DemoDataResult(100, 100), demo.generate(100));
        assertEquals(CardOrder.Status.PENDING, cards.getOrder(existing.id()).status());
        assertNull(cards.getOrder(existing.id()).cardNumber());
        assertEquals(100, db.queryForObject("""
                SELECT COUNT(*) FROM shipment_orders s JOIN card_orders c ON s.card_order_id=c.id
                WHERE c.status='GENERATED' AND s.status='PENDING'
                  AND c.created_by='DEMO' AND c.updated_by='DEMO'
                  AND s.created_by='DEMO' AND s.updated_by='DEMO'
                """, Integer.class));
        assertEquals(100, db.queryForObject("SELECT COUNT(DISTINCT card_number) FROM card_orders", Integer.class));
        assertEquals(4, db.queryForObject("SELECT COUNT(DISTINCT card_type_id) FROM card_orders WHERE created_by='DEMO'", Integer.class));
    }

    @Test
    void laterFailureRollsBackTheEntireBatch() {
        doReturn(new Recipient("Test", "User", "invalid", "Київ", "1")).when(recipients).create(1);

        assertThrows(DataIntegrityViolationException.class, () -> demo.generate(2));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM card_orders", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM shipment_orders", Integer.class));
    }
}
