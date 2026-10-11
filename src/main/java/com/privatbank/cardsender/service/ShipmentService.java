package com.privatbank.cardsender.service;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.repository.CardOrderRepository;
import com.privatbank.cardsender.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private final CardOrderRepository cards;
    private final ShipmentRepository shipments;

    @Transactional
    public ShipmentOrder create(String cardNumber, Recipient recipient, Operator operator) {
        CardOrder card = findGeneratedCard(cardNumber);
        try {
            return shipments.saveAndFlush(ShipmentOrder.pending(card.id(), recipient, operator));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateShipment(exception)) {
                throw new BusinessException(BusinessException.Kind.CONFLICT, "A shipment already exists for this card");
            }
            throw exception;
        }
    }

    public OrderStatus getStatus(String cardNumber) {
        CardOrder card = findGeneratedCard(cardNumber);
        return shipments.findByCardOrderId(card.id())
                .map(shipment -> new OrderStatus(
                        card.id(),
                        cardNumber,
                        shipment.status(),
                        shipment.waybill(),
                        shipment.audit().updatedAt()))
                .orElseGet(() -> new OrderStatus(
                        card.id(),
                        cardNumber,
                        null,
                        null,
                        card.audit().updatedAt())
                );
    }

    /** Перевіряє, що переданий номер ЕН належить відправленню цієї картки. */
    public OrderStatus getStatus(String cardNumber, String waybill) {
        if (waybill == null) {
            return getStatus(cardNumber);
        }
        if (!waybill.matches("[0-9]{1,30}")) {
            throw new BusinessException(BusinessException.Kind.INVALID_REQUEST,
                    "Expected a waybill number containing 1 to 30 digits");
        }
        OrderStatus status = getStatus(cardNumber);
        if (!waybill.equals(status.waybill())) {
            throw new BusinessException(BusinessException.Kind.INVALID_REQUEST,
                    "Waybill does not match the shipment for this card");
        }
        return status;
    }

    private CardOrder findGeneratedCard(String cardNumber) {
        return cards.findByCardNumber(cardNumber)
                .filter(card -> card.status() == CardOrder.Status.GENERATED)
                .orElseThrow(() -> new BusinessException(BusinessException.Kind.NOT_FOUND, "Generated card not found"));
    }

    private boolean isDuplicateShipment(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if (constraint != null && constraint.toUpperCase(java.util.Locale.ROOT)
                        .replace("\"", "").matches("(?:.*\\.)?UQ_SO_CARD_ORDER")) {
                    return true;
                }
            }
        }
        return false;
    }
}
