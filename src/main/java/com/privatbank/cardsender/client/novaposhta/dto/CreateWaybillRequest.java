package com.privatbank.cardsender.client.novaposhta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatbank.cardsender.delivery.NovaPoshtaProperties;
import com.privatbank.cardsender.domain.Recipient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record CreateWaybillRequest(
        @JsonProperty("PayerType") String payerType,
        @JsonProperty("PaymentMethod") String paymentMethod,
        @JsonProperty("DateTime") String dateTime,
        @JsonProperty("CargoType") String cargoType,
        @JsonProperty("Weight") String weight,
        @JsonProperty("ServiceType") String serviceType,
        @JsonProperty("SeatsAmount") String seatsAmount,
        @JsonProperty("Description") String description,
        @JsonProperty("Cost") String cost,
        @JsonProperty("Sender") String sender,
        @JsonProperty("CitySender") String citySender,
        @JsonProperty("SenderWarehouseIndex") String senderWarehouseIndex,
        @JsonProperty("ContactSender") String contactSender,
        @JsonProperty("SendersPhone") String sendersPhone,
        @JsonProperty("NewAddress") String newAddress,
        @JsonProperty("RecipientCityName") String recipientCityName,
        @JsonProperty("RecipientAddressName") String recipientAddressName,
        @JsonProperty("RecipientName") String recipientName,
        @JsonProperty("RecipientType") String recipientType,
        @JsonProperty("RecipientsPhone") String recipientsPhone,
        @JsonProperty("SettlementType") String settlementType) {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Це я готовий обговорити на співбесіді, коли ви будете питати мене за цей код, якщо будете звісно :D
     * Можна було маппер використати, але я особисто обрав це метод через те, що він використовується лише раз,
     * тому не бачу сенсу в маппері.
     */
    public static CreateWaybillRequest from(NovaPoshtaProperties properties,
                                            Recipient recipient,
                                            LocalDate shipmentDate) {
        return new CreateWaybillRequest(
                "Sender",
                "Cash",
                shipmentDate.format(DATE_FORMAT),
                "Documents",
                "0.1",
                "WarehouseWarehouse",
                "1",
                "Документи",
                "300.00",
                properties.sender(),
                properties.citySender(),
                properties.senderWarehouseIndex(),
                properties.contactSender(),
                properties.sendersPhone(),
                "1",
                recipient.city(),
                recipient.warehouse(),
                recipient.lastName() + " " + recipient.firstName(),
                "PrivatePerson",
                recipient.phone(),
                "м."
        );
    }

    @Override
    public String toString() {
        return "CreateWaybillRequest[details=MASKED]";
    }
}
