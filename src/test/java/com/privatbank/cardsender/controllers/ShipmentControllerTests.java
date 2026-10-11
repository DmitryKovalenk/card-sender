package com.privatbank.cardsender.controllers;

import com.privatbank.cardsender.advisors.ApiExceptionHandler;
import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShipmentControllerTests {
    private final ShipmentService shipments = mock(ShipmentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ShipmentController(shipments))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    private final String number = "0000000000000000";
    private final Recipient recipient = new Recipient("Дмитро", "Коваленко", "380671234567", "Київ", "1");
    private final String request = """
            {"firstName":"Дмитро","lastName":"Коваленко","cardNumber":"0000000000000000",
             "phone":"380671234567","city":"Київ","warehouse":"1"}
            """;

    @Test
    void creationPreservesRecipientAndLeadingZeros() throws Exception {
        when(shipments.create(number, recipient, new Operator("tester")))
                .thenReturn(ShipmentOrder.pending(7, recipient, new Operator("tester")));
        mvc.perform(post("/api/shipment-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.CARD_ORDER_ID").value(7))
                .andExpect(jsonPath("$.STATUS").value("PENDING"));
        verify(shipments).create(number, recipient, new Operator("tester"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000000000000000", "380671234567", "Київ", "Дмитро", "Коваленко"})
    void invalidRequiredFieldsNeverReachService(String value) throws Exception {
        mvc.perform(post("/api/shipment-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(request.replace(value, "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(shipments);
    }

    @Test
    void duplicateShipmentReturnsConflict() throws Exception {
        when(shipments.create(anyString(), any(), any())).thenThrow(
                new BusinessException(BusinessException.Kind.CONFLICT, "A shipment already exists for this card"));
        mvc.perform(post("/api/shipment-orders").header("X-Operator", "tester")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A shipment already exists for this card"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "123", "00000000000000000"})
    void malformedStatusNumberNeverReachesService(String value) throws Exception {
        mvc.perform(get("/api/orders/status/" + value)).andExpect(status().isBadRequest());
        verifyNoInteractions(shipments);
    }

    @Test
    void generatedCardWithoutShipmentHasPublicStatus() throws Exception {
        when(shipments.getStatus(number, null)).thenReturn(new OrderStatus(7, number, null, null, null));
        mvc.perform(get("/api/orders/status/" + number)).andExpect(status().isOk())
                .andExpect(jsonPath("$.STATUS").value("CARD_GENERATED"))
                .andExpect(jsonPath("$.CARD_NUMBER").value(number));
    }

    @ParameterizedTest
    @EnumSource(ShipmentOrder.Status.class)
    void exposesEveryShipmentStatus(ShipmentOrder.Status state) throws Exception {
        when(shipments.getStatus(number, null)).thenReturn(new OrderStatus(7, number, state,
                state == ShipmentOrder.Status.SENT ? "00123" : null, null));
        mvc.perform(get("/api/orders/status/" + number)).andExpect(status().isOk())
                .andExpect(jsonPath("$.STATUS").value(state.name()));
    }

    @Test
    void optionalWaybillIsPassedWithoutLosingLeadingZeros() throws Exception {
        when(shipments.getStatus(number, "00123"))
                .thenReturn(new OrderStatus(7, number, ShipmentOrder.Status.SENT, "00123", null));
        mvc.perform(get("/api/orders/status/" + number).param("waybill", "00123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.WAYBILL").value("00123"));
        verify(shipments).getStatus(number, "00123");
    }

    @Test
    void waybillMismatchReturnsBadRequestWithErrorBody() throws Exception {
        when(shipments.getStatus(number, "99999")).thenThrow(new BusinessException(
                BusinessException.Kind.INVALID_REQUEST, "Waybill does not match the shipment for this card"));
        mvc.perform(get("/api/orders/status/" + number).param("waybill", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Waybill does not match the shipment for this card"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "1234567890123456789012345678901"})
    void malformedWaybillProduces400ThroughRealService(String waybill) throws Exception {
        var cards = mock(com.privatbank.cardsender.repository.CardOrderRepository.class);
        var repository = mock(com.privatbank.cardsender.repository.ShipmentRepository.class);
        var realMvc = MockMvcBuilders.standaloneSetup(new ShipmentController(new ShipmentService(cards, repository)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        realMvc.perform(get("/api/orders/status/" + number).param("waybill", waybill))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Expected a waybill number containing 1 to 30 digits"));
        verifyNoInteractions(cards, repository);
    }
}
