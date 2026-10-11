package com.privatbank.cardsender;

import com.privatbank.cardsender.domain.CreateCardInput;
import com.privatbank.cardsender.domain.Operator;
import com.privatbank.cardsender.domain.CardTypes;
import com.privatbank.cardsender.service.CardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "ORACLE_TEST_URL", matches = ".+")
class CardSenderApplicationTests extends OracleIntegrationTest {
    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate db;
    @Autowired
    CardService cards;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM shipment_orders");
        db.update("DELETE FROM card_orders");
        db.update("UPDATE card_types SET is_active=1");
    }

    @Test
    void orderGenerationAndShipmentLifecycle() throws Exception {
        mvc.perform(get("/api/card-types")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        String body = mvc.perform(post("/api/card-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(cardRequest("ELITE")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.CARD_NUMBER").isEmpty())
                .andExpect(jsonPath("$.STATUS").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = new ObjectMapper().readTree(body).path("ID").asLong();
        mvc.perform(post("/api/card-orders/generate").header("X-Operator", "generator"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generated").value(1));
        String number = cards.getOrder(id).cardNumber();
        assertTrue(number.startsWith("000000"));
        assertEquals(16, number.length());
        assertEquals("generator", cards.getOrder(id).audit().updatedBy());
        assertEquals(0, cards.generatePending(new Operator("generator")));
        mvc.perform(get("/api/orders/status/" + number)).andExpect(jsonPath("$.STATUS").value("CARD_GENERATED"));
        String shipment = """
                {"firstName":"Дмитро","lastName":"Коваленко","cardNumber":"%s",
                 "phone":"380671234567","city":"Київ","warehouse":"1"}
                """.formatted(number);
        mvc.perform(post("/api/shipment-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(shipment))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.STATUS").value("PENDING"))
                .andExpect(jsonPath("$.CARD_ORDER_ID").value(id));
        mvc.perform(post("/api/shipment-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(shipment))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("A shipment already exists for this card"));
        mvc.perform(get("/api/orders/status/" + number)).andExpect(jsonPath("$.STATUS").value("PENDING"));
    }

    @Test
    void validationAndInactiveType() throws Exception {
        long type = typeId("444444");
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester").contentType(MediaType.APPLICATION_JSON)
                        .content(cardRequest("GOLD").replace("Dmytro", "Дмитро")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester").contentType(MediaType.APPLICATION_JSON)
                        .content(cardRequest("UNKNOWN")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Test\",\"lastName\":\"User\",\"cardType\":null}"))
                .andExpect(status().isBadRequest());
        db.update("UPDATE card_types SET is_active=0 WHERE id=?", type);
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester").contentType(MediaType.APPLICATION_JSON)
                        .content(cardRequest("GOLD")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/status/4444440000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/status/invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void bulkGenerationPersistsUniqueNumbers() {
        for (int i = 0; i < 100; i++) {
            cards.create(new CreateCardInput("Test", "User", CardTypes.GOLD), new Operator("tester"));
        }
        assertEquals(100, cards.generatePending(new Operator("generator")));
        assertEquals(100, db.queryForObject("SELECT COUNT(DISTINCT card_number) FROM card_orders", Integer.class));
        assertEquals(100, db.queryForObject("SELECT COUNT(*) FROM card_orders WHERE status='GENERATED'", Integer.class));
    }

    private long typeId(String bin) {
        return db.queryForObject("SELECT id FROM card_types WHERE bin=?", Long.class, bin);
    }

    private String cardRequest(String cardType) {
        return """
                {"firstName":"Dmytro","lastName":"Kovalenko","cardType":"%s"}
                """.formatted(cardType);
    }
}
