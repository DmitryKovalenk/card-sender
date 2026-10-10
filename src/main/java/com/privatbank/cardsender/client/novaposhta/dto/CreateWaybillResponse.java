package com.privatbank.cardsender.client.novaposhta.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatbank.cardsender.client.novaposhta.serialization.StrictBooleanDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateWaybillResponse(
        @JsonProperty("success") @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean success,
        @JsonProperty("data") List<CreatedWaybill> data,
        @JsonProperty("errors") List<String> errors) {
}
