package com.privatbank.cardsender.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatbank.cardsender.domain.CardOrder;

import java.time.OffsetDateTime;

public record CardOrderResponse(
        @JsonProperty("ID") Long id,
        @JsonProperty("FIRST_NAME") String firstName,
        @JsonProperty("LAST_NAME") String lastName,
        @JsonProperty("CARD_TYPE_ID") Long cardTypeId,
        @JsonProperty("CARD_NUMBER") String cardNumber,
        @JsonProperty("STATUS") CardOrder.Status status,
        @JsonProperty("OPERATOR") String operator,
        @JsonProperty("CREATED_AT") OffsetDateTime createdAt) {

    public static CardOrderResponse from(CardOrder card) {
        return new CardOrderResponse(
                card.id(),
                card.firstName(),
                card.lastName(),
                card.cardTypeId(),
                card.cardNumber(),
                card.status(),
                card.audit().createdBy(),
                card.audit().createdAt()
        );
    }
}
