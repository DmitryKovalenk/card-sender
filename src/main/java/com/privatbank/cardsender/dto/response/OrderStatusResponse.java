package com.privatbank.cardsender.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatbank.cardsender.domain.OrderStatus;

import java.time.OffsetDateTime;

public record OrderStatusResponse(
        @JsonProperty("CARD_ORDER_ID") Long cardOrderId,
        @JsonProperty("CARD_NUMBER") String cardNumber,
        @JsonProperty("STATUS") String status,
        @JsonProperty("WAYBILL") String waybill,
        @JsonProperty("UPDATED_AT") OffsetDateTime updatedAt) {

    public static OrderStatusResponse from(OrderStatus order) {
        return new OrderStatusResponse(
                order.cardOrderId(),
                order.cardNumber(),
                order.shipmentStatus() == null ? "CARD_GENERATED" : order.shipmentStatus().name(),
                order.waybill(),
                order.updatedAt()
        );
    }
}
