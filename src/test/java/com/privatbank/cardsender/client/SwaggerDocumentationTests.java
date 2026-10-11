package com.privatbank.cardsender.client;

import com.privatbank.cardsender.advisors.ApiExceptionHandler;
import com.privatbank.cardsender.config.OpenApiConfiguration;
import com.privatbank.cardsender.controllers.CardController;
import com.privatbank.cardsender.controllers.DemoController;
import com.privatbank.cardsender.controllers.ShipmentController;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.ShipmentService;
import com.privatbank.cardsender.service.demo.DemoDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SwaggerDocumentationTests.TestApplication.class)
class SwaggerDocumentationTests {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({CardController.class, ShipmentController.class, DemoController.class, ApiExceptionHandler.class, OpenApiConfiguration.class})
    static class TestApplication {
    }

    @MockitoBean
    CardService cards;
    @MockitoBean
    ShipmentService shipments;
    @MockitoBean
    DemoDataService demoData;
    @Autowired
    WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void documentsAllEndpointsAndTheirContracts() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Card Sender API"))
                .andReturn().getResponse().getContentAsString();
        var document = new ObjectMapper().readTree(json);
        var paths = document.path("paths");
        assertEquals(7, paths.size());
        assertTrue(paths.path("/demo/generate").has("post"));
        assertTrue(paths.path("/api/card-types").has("get"));
        assertTrue(paths.path("/api/card-orders/{id}").has("get"));
        assertTrue(paths.path("/api/card-orders/generate").has("post"));
        assertTrue(paths.path("/api/orders/status/{cardNumber}").has("get"));
        var statusParameters = paths.path("/api/orders/status/{cardNumber}").path("get").path("parameters");
        boolean optionalWaybillDocumented = false;
        for (var parameter : statusParameters) {
            if ("waybill".equals(parameter.path("name").asText())) {
                assertEquals("query", parameter.path("in").asText());
                assertFalse(parameter.path("required").asBoolean());
                optionalWaybillDocumented = true;
            }
        }
        assertTrue(optionalWaybillDocumented);
        assertTrue(paths.path("/api/shipment-orders").path("post").path("responses").has("201"));
        var create = paths.path("/api/card-orders").path("post");
        var header = create.path("parameters").get(0);
        assertEquals("X-Operator", header.path("name").asText());
        assertEquals("header", header.path("in").asText());
        assertTrue(header.path("required").asBoolean());
        String responseRef = create.path("responses").path("201").path("content")
                .path("application/json").path("schema").path("$ref").asText();
        assertFalse(responseRef.isEmpty());
        assertTrue(document.at(responseRef.substring(1)).path("properties").has("CARD_NUMBER"));
        assertEquals("Dmytro", document.path("components").path("schemas").path("CardRequest")
                .path("properties").path("firstName").path("example").asText());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
