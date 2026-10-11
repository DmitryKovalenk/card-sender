package com.privatbank.cardsender.advisors;

import com.privatbank.cardsender.controllers.CardController;
import com.privatbank.cardsender.controllers.DemoController;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.demo.DemoDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTests {
    private final CardService cards = mock(CardService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CardController(cards),
                    new DemoController(mock(DemoDataService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    private final String validBody = """
            {"firstName":"Test","lastName":"User","cardType":"GOLD"}
            """;

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"cardType\":\"SECRET_UNKNOWN_VALUE\"}"})
    void malformedJsonAndEnumValuesUseSafeErrorBody(String body) throws Exception {
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Malformed JSON or invalid request value\"}"));
        verifyNoInteractions(cards);
    }

    @Test
    void missingHeaderUsesSameErrorContract() throws Exception {
        mvc.perform(post("/api/card-orders").contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header: X-Operator"));
    }

    @Test
    void invalidPathVariableDoesNotEchoValue() throws Exception {
        mvc.perform(get("/api/card-orders/private-value"))
                .andExpect(status().isBadRequest()).andExpect(content().json(
                        "{\"error\":\"Invalid value for parameter: id\"}"));
    }

    @Test
    void invalidQueryParameterUsesSameErrorContract() throws Exception {
        mvc.perform(post("/demo/generate").param("count", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter: count"));
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(delete("/api/card-orders/1"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void unsupportedContentTypeUsesSameErrorContract() throws Exception {
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester")
                        .contentType(MediaType.TEXT_PLAIN).content(validBody))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalMessage() throws Exception {
        when(cards.getOrder(1)).thenThrow(new IllegalStateException("SQL password=secret"));
        mvc.perform(get("/api/card-orders/1")).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"error\":\"Internal server error\"}"));
    }

    @Test
    void unknownEndpointUsesSameErrorContract() throws Exception {
        mvc.perform(get("/api/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
