package com.privatbank.cardsender.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatbank.cardsender.domain.CardType;

import java.time.OffsetDateTime;

public record CardTypeResponse(
        @JsonProperty("ID") Long id,
        @JsonProperty("NAME") String name,
        @JsonProperty("BIN") String bin,
        @JsonProperty("ACTIVE") Boolean active,
        @JsonProperty("OPERATOR") String operator,
        @JsonProperty("CREATED_AT") OffsetDateTime createdAt) {

    public static CardTypeResponse from(CardType type) {
        return new CardTypeResponse(
                type.id(),
                type.name(),
                type.bin(),
                type.active(),
                type.audit().createdBy(),
                type.audit().createdAt());
    }
}
