package com.privatbank.cardsender.service;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.domain.CardTypes;
import com.privatbank.cardsender.repository.CardOrderRepository;
import com.privatbank.cardsender.repository.CardTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CardServiceTests {
    private final CardTypeRepository types = mock(CardTypeRepository.class);
    private final CardOrderRepository orders = mock(CardOrderRepository.class);
    private final jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
    private final CardService service = new CardService(types, orders, entityManager);
    private final Operator operator = new Operator("tester");

    @Test
    void inactiveTypeCannotCreateAnOrder() {
        when(types.findByBinForUpdate("444444")).thenReturn(Optional.of(new CardType(1, "Gold", "444444", false, null)));
        var exception = assertThrows(BusinessException.class, () -> service.create(new CreateCardInput("Test", "User", CardTypes.GOLD), operator));
        assertEquals(BusinessException.Kind.INVALID_REQUEST, exception.kind());
        verifyNoInteractions(orders);
    }

    @Test
    void creationDoesNotGenerateACardNumber() {
        var card = new CardOrder(10, 1, "Test", "User", null, CardOrder.Status.PENDING, null, null);
        when(types.findByBinForUpdate("444444")).thenReturn(Optional.of(new CardType(81, "Gold", "444444", true, null)));
        when(orders.saveAndFlush(any(CardOrder.class))).thenReturn(card);
        assertEquals(card, service.create(new CreateCardInput("Test", "User", CardTypes.GOLD), operator));
        verify(orders).saveAndFlush(argThat(order -> order.cardTypeId() == 81
                                                     && order.status() == CardOrder.Status.PENDING && order.cardNumber() == null));
        verify(orders, never()).generateNumber(anyLong(), any());
    }

    @Test
    void generationProcessesOnlyLockedOrdersWithTheOperator() {
        when(orders.lockPendingIds()).thenReturn(List.of(10L, 11L));
        assertEquals(2, service.generatePending(operator));
        verify(orders).generateNumber(10, operator.value());
        verify(orders).generateNumber(11, operator.value());
    }

    @Test
    void missingOrderIsADomainFailure() {
        when(orders.findById(99L)).thenReturn(Optional.empty());
        assertEquals(BusinessException.Kind.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.getOrder(99)).kind());
    }

    @Test
    void missingTypeCannotCreateAnOrder() {
        assertEquals(BusinessException.Kind.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.create(new CreateCardInput("Test", "User", CardTypes.GOLD), operator)).kind());
        verifyNoInteractions(orders);
    }

    @Test
    void singleGenerationFlushesBeforeProcedureAndClearsStaleEntitiesAfterwards() {
        when(orders.findById(10L)).thenReturn(Optional.of(
                CardOrder.pending("Test", "User", 1, operator)));
        when(orders.generateNumber(10, "tester")).thenReturn("0000000000000000");
        assertEquals("0000000000000000", service.generateNumber(10, operator));
        var sequence = inOrder(orders, entityManager);
        sequence.verify(orders).findById(10L);
        sequence.verify(entityManager).flush();
        sequence.verify(orders).generateNumber(10, "tester");
        sequence.verify(entityManager).clear();
    }

    @Test
    void missingOrderNeverInvokesTheGenerationProcedure() {
        assertEquals(BusinessException.Kind.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.generateNumber(99, operator)).kind());
        verify(orders, never()).generateNumber(anyLong(), any());
        verifyNoInteractions(entityManager);
    }

    @Test
    void emptyPendingBatchDoesNotInvokeTheProcedure() {
        when(orders.lockPendingIds()).thenReturn(List.of());
        assertEquals(0, service.generatePending(operator));
        verify(orders, never()).generateNumber(anyLong(), any());
    }
}
