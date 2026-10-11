package com.privatbank.cardsender.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreatedWaybill(@JsonProperty("IntDocNumber") String number) {
}
