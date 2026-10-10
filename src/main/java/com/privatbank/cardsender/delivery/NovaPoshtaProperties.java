package com.privatbank.cardsender.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("nova-poshta")
public record NovaPoshtaProperties(
        boolean enabled,
        String apiKey,
        @DefaultValue("https://api.novaposhta.ua/v2.0/json/") URI url,
        String sender,
        String citySender,
        String senderWarehouseIndex,
        String contactSender,
        String sendersPhone,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout,
        @DefaultValue("20") int batchSize) {

    public NovaPoshtaProperties {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("nova-poshta.batch-size must be between 1 and 1000");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
            || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Nova Poshta timeouts must be positive");
        }
        if (url == null || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
            throw new IllegalArgumentException("nova-poshta.url must be an HTTP(S) URL");
        }
        if (enabled) {
            require(apiKey, "api-key");
            require(sender, "sender");
            require(citySender, "city-sender");
            require(senderWarehouseIndex, "sender-warehouse-index");
            require(contactSender, "contact-sender");
            require(sendersPhone, "senders-phone");
        }
    }

    private static void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing nova-poshta." + property);
        }
    }

    @Override
    public String toString() {
        return "NovaPoshtaProperties[enabled=" + enabled + ", credentials=REDACTED]";
    }
}
