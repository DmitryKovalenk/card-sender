package com.privatbank.cardsender.service;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.repository.CardOrderRepository;
import com.privatbank.cardsender.repository.ShipmentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentServiceTests {
    private final CardOrderRepository cards = mock(CardOrderRepository.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ShipmentService service = new ShipmentService(cards, shipments);
    private final Recipient recipient = new Recipient("Test", "User", "380671234567", "Київ", "1");
    private final Operator operator = new Operator("tester");
    private final String number = "4444440000000008";

    @Test
    void missingGeneratedCardCannotBeShipped() {
        when(cards.findByCardNumber(number)).thenReturn(Optional.empty());
        assertEquals(BusinessException.Kind.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.create(number, recipient, operator)).kind());
        verifyNoInteractions(shipments);
    }

    @Test
    void duplicateShipmentBecomesABusinessConflict() {
        when(cards.findByCardNumber(number)).thenReturn(Optional.of(
                new CardOrder(1, 1, "Test", "User", number, CardOrder.Status.GENERATED, null, null)));
        when(shipments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("duplicate", "23000", 1), "UQ_SO_CARD_ORDER")));
        assertEquals(BusinessException.Kind.CONFLICT, assertThrows(BusinessException.class,
                () -> service.create(number, recipient, operator)).kind());
    }

    @Test
    void pendingCardCannotBeShipped() {
        when(cards.findByCardNumber(number)).thenReturn(Optional.of(CardOrder.pending("Test", "User", 1, operator)));
        assertEquals(BusinessException.Kind.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.create(number, recipient, operator)).kind());
        verifyNoInteractions(shipments);
    }

    @Test
    void createsPendingShipmentWithRecipientAndAudit() {
        generatedCard();
        when(shipments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(number, recipient, operator);
        assertEquals(1, result.cardOrderId());
        assertEquals(recipient, result.recipient());
        assertEquals(ShipmentOrder.Status.PENDING, result.status());
        assertNull(result.waybill());
        assertEquals("tester", result.audit().createdBy());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UQ_SO_CARD_ORDER", "schema.uq_so_card_order", "\"SCHEMA\".\"UQ_SO_CARD_ORDER\""})
    void recognizesQualifiedDuplicateConstraints(String constraint) {
        generatedCard();
        when(shipments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("failed",
                new ConstraintViolationException("failed", new SQLException(), constraint)));
        assertEquals(BusinessException.Kind.CONFLICT, assertThrows(BusinessException.class,
                () -> service.create(number, recipient, operator)).kind());
    }

    @Test
    void unrelatedIntegrityFailureIsNotReportedAsDuplicate() {
        generatedCard();
        var failure = new DataIntegrityViolationException("failed",
                new ConstraintViolationException("failed", new SQLException(), "CK_PHONE"));
        when(shipments.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.create(number, recipient, operator)));
    }

    @Test
    void statusWithoutShipmentUsesCardTimestamp() {
        var card = generatedCard();
        var status = service.getStatus(number);
        assertEquals(1, status.cardOrderId());
        assertEquals(number, status.cardNumber());
        assertNull(status.shipmentStatus());
        assertNull(status.waybill());
        assertEquals(card.audit().updatedAt(), status.updatedAt());
    }

    @Test
    void statusWithShipmentUsesWaybillAndShipmentTimestamp() {
        generatedCard();
        var timestamp = java.time.OffsetDateTime.parse("2026-10-10T12:00:00Z");
        when(shipments.findByCardOrderId(1)).thenReturn(Optional.of(new ShipmentOrder(2, 1, recipient,
                ShipmentOrder.Status.SENT, "00123", new Audit("tester", timestamp, "worker", timestamp))));
        var status = service.getStatus(number);
        assertEquals(ShipmentOrder.Status.SENT, status.shipmentStatus());
        assertEquals("00123", status.waybill());
        assertEquals(timestamp, status.updatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "00123", "123456789012345678901234567890"})
    void matchingWaybillReturnsStoredStatus(String waybill) {
        var card = generatedCard();
        when(shipments.findByCardOrderId(1)).thenReturn(Optional.of(new ShipmentOrder(2, 1, recipient,
                ShipmentOrder.Status.SENT, waybill, card.audit())));
        assertEquals(service.getStatus(number), service.getStatus(number, waybill));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "1234567890123456789012345678901", "+123", " 00123"})
    void malformedWaybillIsRejectedBeforeDatabaseLookup(String waybill) {
        assertEquals(BusinessException.Kind.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.getStatus(number, waybill)).kind());
        verifyNoInteractions(cards, shipments);
    }

    @Test
    void mismatchedWaybillDoesNotReturnAnotherShipmentsStatus() {
        var card = generatedCard();
        when(shipments.findByCardOrderId(1)).thenReturn(Optional.of(new ShipmentOrder(2, 1, recipient,
                ShipmentOrder.Status.SENT, "00123", card.audit())));
        assertEquals(BusinessException.Kind.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.getStatus(number, "123")).kind());
    }

    @Test
    void suppliedWaybillIsRejectedWhenShipmentDoesNotExist() {
        generatedCard();
        assertEquals(BusinessException.Kind.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.getStatus(number, "00123")).kind());
        assertNull(service.getStatus(number, null).shipmentStatus());
    }

    @Test
    void suppliedWaybillIsRejectedBeforeWaybillCreation() {
        var card = generatedCard();
        when(shipments.findByCardOrderId(1)).thenReturn(Optional.of(new ShipmentOrder(2, 1, recipient,
                ShipmentOrder.Status.PENDING, null, card.audit())));
        assertEquals(BusinessException.Kind.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.getStatus(number, "00123")).kind());
        assertEquals(ShipmentOrder.Status.PENDING, service.getStatus(number, null).shipmentStatus());
    }

    @Test
    void unknownCardStillReturnsNotFoundWhenWaybillSupplied() {
        assertEquals(BusinessException.Kind.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.getStatus(number, "00123")).kind());
        verifyNoInteractions(shipments);
    }

    private CardOrder generatedCard() {
        var timestamp = java.time.OffsetDateTime.parse("2026-10-09T12:00:00Z");
        var card = new CardOrder(1, 1, "Test", "User", number, CardOrder.Status.GENERATED, timestamp,
                new Audit("tester", timestamp, "tester", timestamp));
        when(cards.findByCardNumber(number)).thenReturn(Optional.of(card));
        return card;
    }
}
