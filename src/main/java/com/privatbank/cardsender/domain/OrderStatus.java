package com.privatbank.cardsender.domain;

import java.time.OffsetDateTime;

public record OrderStatus(long cardOrderId, String cardNumber, ShipmentOrder.Status shipmentStatus,
                          String waybill, OffsetDateTime updatedAt) {
}
