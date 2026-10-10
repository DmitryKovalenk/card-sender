package com.privatbank.cardsender.controllers;

import com.privatbank.cardsender.advisors.ApiExceptionHandler;
import com.privatbank.cardsender.domain.BusinessException;
import com.privatbank.cardsender.domain.CardOrder;
import com.privatbank.cardsender.domain.CreateCardInput;
import com.privatbank.cardsender.domain.Operator;
import com.privatbank.cardsender.dto.CardTypes;
import com.privatbank.cardsender.service.CardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardControllerTests {
    private final CardService cards = mock(CardService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CardController(cards))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void creationMapsHttpRequestToServiceInputAndReturnsPendingOrder() throws Exception {
        when(cards.create(new CreateCardInput("Anne-Marie", "O'Neil", CardTypes.ELITE), new Operator("tester")))
                .thenReturn(CardOrder.pending("Anne-Marie", "O'Neil", 4, new Operator("tester")));
        mvc.perform(post("/api/card-orders").header("X-Operator", " tester ")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"firstName":"Anne-Marie","lastName":"O'Neil","cardType":"ELITE"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.FIRST_NAME").value("Anne-Marie"))
                .andExpect(jsonPath("$.CARD_TYPE_ID").value(4)).andExpect(jsonPath("$.STATUS").value("PENDING"))
                .andExpect(jsonPath("$.CARD_NUMBER").isEmpty()).andExpect(jsonPath("$.OPERATOR").value("tester"));
        verify(cards).create(new CreateCardInput("Anne-Marie", "O'Neil", CardTypes.ELITE), new Operator("tester"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{", "{\"firstName\":\"Дмитро\",\"lastName\":\"User\",\"cardType\":\"GOLD\"}",
            "{\"firstName\":\"Test\",\"lastName\":\"User\",\"cardType\":\"UNKNOWN\"}",
            "{\"firstName\":\"Test\",\"lastName\":\"User\",\"cardType\":null}"})
    void invalidRequestsNeverReachTheService(String body) throws Exception {
        mvc.perform(post("/api/card-orders").header("X-Operator", "tester")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(cards);
    }

    @Test
    void creationRequiresOperator() throws Exception {
        mvc.perform(post("/api/card-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Test\",\"lastName\":\"User\",\"cardType\":\"GOLD\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cards);
    }

    @Test
    void generationUsesDefaultOperator() throws Exception {
        when(cards.generatePending(new Operator("CARD_GENERATOR"))).thenReturn(3);
        mvc.perform(post("/api/card-orders/generate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.generated").value(3));
        verify(cards).generatePending(new Operator("CARD_GENERATOR"));
    }

    @Test
    void missingOrderReturns404WithDomainMessage() throws Exception {
        when(cards.getOrder(99)).thenThrow(new BusinessException(BusinessException.Kind.NOT_FOUND, "Card order not found"));
        mvc.perform(get("/api/card-orders/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Card order not found"));
    }
}
