package com.privatbank.cardsender.service.demo;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.domain.CardTypes;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoDataServiceTests {
    private final CardService cards = mock(CardService.class);
    private final ShipmentService shipments = mock(ShipmentService.class);
    private final DemoDataService service = new DemoDataService(cards, shipments, new DemoRecipientFactory());

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1001})
    void rejectsInvalidCountBeforeCreatingAnything(int count) {
        assertEquals(BusinessException.Kind.INVALID_REQUEST,
                assertThrows(BusinessException.class, () -> service.generate(count)).kind());
        verifyNoInteractions(cards, shipments);
    }

    @Test
    void requiresAnActiveCardType() {
        when(cards.listTypes()).thenReturn(List.of(new CardType(1, "Inactive", "444444", false, null)));
        assertEquals(BusinessException.Kind.CONFLICT,
                assertThrows(BusinessException.class, () -> service.generate(1)).kind());
        verify(cards, never()).create(any(), any());
        verifyNoInteractions(shipments);
    }

    @Test
    void cyclesActiveTypesAndShipsOnlyTheNewlyGeneratedCards() {
        when(cards.listTypes()).thenReturn(List.of(
                new CardType(91, "Gold", "444444", true, null),
                new CardType(2, "Inactive", "555555", false, null),
                new CardType(73, "Wood", "666666", true, null)));
        var nextId = new AtomicLong(10);
        when(cards.create(any(), any())).thenAnswer(call -> {
            CreateCardInput command = call.getArgument(0);
            return new CardOrder(nextId.getAndIncrement(), command.cardType() == CardTypes.GOLD ? 91 : 73, command.firstName(), command.lastName(),
                    null, CardOrder.Status.PENDING, null, null);
        });
        when(cards.generateNumber(anyLong(), any())).thenAnswer(call -> "generated-" + call.getArgument(0));

        assertEquals(new DemoDataResult(3, 3), service.generate(3));

        var operator = new Operator("DEMO");
        verify(cards, times(2)).create(new CreateCardInput("Test", "User", CardTypes.GOLD), operator);
        verify(cards).create(new CreateCardInput("Test", "User", CardTypes.WOODEN), operator);
        for (long id = 10; id < 13; id++) {
            verify(cards).generateNumber(id, operator);
            verify(shipments).create(eq("generated-" + id), any(), eq(operator));
        }
        verify(cards, never()).generatePending(any());
    }

    @Test
    void unsupportedDatabaseTypesCannotProduceFakeDemoCounts() {
        when(cards.listTypes()).thenReturn(List.of(new CardType(99, "Future", "123456", true, null)));
        assertEquals(BusinessException.Kind.CONFLICT,
                assertThrows(BusinessException.class, () -> service.generate(1)).kind());
        verify(cards, never()).create(any(), any());
        verifyNoInteractions(shipments);
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void acceptsBothBatchBoundariesAndPreservesRecipients(int count) {
        when(cards.listTypes()).thenReturn(List.of(
                new CardType(99, "Unsupported", "123456", true, null),
                new CardType(1, "Gold", "444444", true, null)));
        var nextId = new AtomicLong(1);
        when(cards.create(any(), any())).thenAnswer(call -> new CardOrder(nextId.getAndIncrement(), 1,
                "Test", "User", null, CardOrder.Status.PENDING, null, null));
        when(cards.generateNumber(anyLong(), any())).thenAnswer(call -> "number-" + call.getArgument(0));
        assertEquals(new DemoDataResult(count, count), service.generate(count));
        var operator = new Operator("DEMO");
        verify(cards, times(count)).create(new CreateCardInput("Test", "User", CardTypes.GOLD), operator);
        for (int index = 0; index < count; index++) {
            verify(shipments).create("number-" + (index + 1), new DemoRecipientFactory().create(index), operator);
        }
    }

    @Test
    void generationFailureStopsBatchBeforeShipping() {
        when(cards.listTypes()).thenReturn(List.of(new CardType(1, "Gold", "444444", true, null)));
        when(cards.create(any(), any())).thenReturn(CardOrder.pending("Test", "User", 1, new Operator("DEMO")));
        var failure = new IllegalStateException("procedure failed");
        when(cards.generateNumber(anyLong(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.generate(2)));
        verify(cards, times(1)).create(any(), any());
        verifyNoInteractions(shipments);
    }
}
