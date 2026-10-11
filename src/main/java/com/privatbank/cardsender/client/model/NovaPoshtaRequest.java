package com.privatbank.cardsender.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NovaPoshtaRequest<T>(
        @JsonProperty("apiKey") String apiKey,
        @JsonProperty("modelName") String modelName,
        @JsonProperty("calledMethod") String calledMethod,
        @JsonProperty("methodProperties") T methodProperties) {

    @Override
    public String toString() {
        return "NovaPoshtaRequest[modelName=" + modelName + ", calledMethod=" + calledMethod
               + ", apiKey=REDACTED, methodProperties=REDACTED]";
    }
}
