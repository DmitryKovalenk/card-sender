package com.privatbank.cardsender.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatbank.cardsender.domain.ShipmentOrder;

import java.time.OffsetDateTime;

public record ShipmentOrderResponse(
        @JsonProperty("ID") Long id,
        @JsonProperty("CARD_ORDER_ID") Long cardOrderId,
        @JsonProperty("FIRST_NAME") String firstName,
        @JsonProperty("LAST_NAME") String lastName,
        @JsonProperty("PHONE") String phone,
        @JsonProperty("CITY") String city,
        @JsonProperty("WAREHOUSE") String warehouse,
        @JsonProperty("STATUS") ShipmentOrder.Status status,
        @JsonProperty("WAYBILL") String waybill,
        @JsonProperty("OPERATOR") String operator,
        @JsonProperty("CREATED_AT") OffsetDateTime createdAt,
        @JsonProperty("UPDATED_AT") OffsetDateTime updatedAt) {

    public static ShipmentOrderResponse from(ShipmentOrder shipment) {
        var recipient = shipment.recipient();
        return new ShipmentOrderResponse(
                shipment.id(),
                shipment.cardOrderId(),
                recipient.firstName(),
                recipient.lastName(),
                recipient.phone(),
                recipient.city(),
                recipient.warehouse(),
                shipment.status(),
                shipment.waybill(),
                shipment.audit().createdBy(),
                shipment.audit().createdAt(),
                shipment.audit().updatedAt()
        );
    }
}
