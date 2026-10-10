package com.privatbank.cardsender.delivery;

import com.privatbank.cardsender.client.NovaPoshtaClient;
import com.privatbank.cardsender.domain.DeliveryResult;
import com.privatbank.cardsender.domain.Recipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NovaPoshtaClientTests {
    private MockRestServiceServer server;
    private NovaPoshtaClient client;
    private final Recipient recipient = new Recipient("Тест", "Клієнт", "380671234567", "Київ", "1");

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new NovaPoshtaProperties(true, "test-key", URI.create("https://carrier.test/"),
                "sender", "city", "31/2", "contact", "380671234567", Duration.ofSeconds(5), Duration.ofSeconds(20), 20);
        client = new NovaPoshtaClient(properties, builder.build(),
                Clock.fixed(Instant.parse("2026-10-08T22:00:00Z"), ZoneId.of("Europe/Kiev")));
    }

    @Test
    void sendsRecipientAndKyivDateAndParsesWaybill() {
        server.expect(requestTo("https://carrier.test/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "apiKey": "test-key",
                          "modelName": "InternetDocument",
                          "calledMethod": "save",
                          "methodProperties": {
                            "PayerType": "Sender", "PaymentMethod": "Cash", "DateTime": "09.10.2026",
                            "CargoType": "Documents", "Weight": "0.1", "ServiceType": "WarehouseWarehouse",
                            "SeatsAmount": "1", "Description": "Документи", "Cost": "300.00",
                            "Sender": "sender", "CitySender": "city", "SenderWarehouseIndex": "31/2",
                            "ContactSender": "contact", "SendersPhone": "380671234567", "NewAddress": "1",
                            "RecipientCityName": "Київ", "RecipientAddressName": "1",
                            "RecipientName": "Клієнт Тест", "RecipientType": "PrivatePerson",
                            "RecipientsPhone": "380671234567", "SettlementType": "м."
                          }
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"success\":true,\"data\":[{\"IntDocNumber\":\"20400000000001\"}]}", MediaType.APPLICATION_JSON));
        assertEquals(DeliveryResult.accepted("20400000000001"), client.send(recipient));
        server.verify();
    }

    @Test
    void explicitRejectionIsDistinctFromAnUncertainOutcome() {
        server.expect(requestTo("https://carrier.test/"))
                .andRespond(withSuccess("{\"success\":false,\"errors\":[\"Rejected\"]}", MediaType.APPLICATION_JSON));
        assertEquals(DeliveryResult.rejected(), client.send(recipient));
        server.verify();
    }

    @Test
    void missingWaybillRequiresReconciliation() {
        server.expect(requestTo("https://carrier.test/"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(DeliveryResult.uncertain(), client.send(recipient));
        server.verify();
    }

    @Test
    void httpFailureDoesNotAssumeCarrierRejectedTheRequest() {
        server.expect(requestTo("https://carrier.test/"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertEquals(DeliveryResult.uncertain(), client.send(recipient));
        server.verify();
    }

    @Test
    void malformedResponseRequiresReconciliation() {
        server.expect(requestTo("https://carrier.test/"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertEquals(DeliveryResult.uncertain(), client.send(recipient));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"success\":null}",
            "{\"success\":\"false\"}", "{\"success\":0}",
            "{\"success\":\"true\",\"data\":[{\"IntDocNumber\":\"20400000000001\"}]}",
            "{\"success\":true}", "{\"success\":true,\"data\":null}",
            "{\"success\":true,\"data\":[null]}", "{\"success\":true,\"data\":[{}]}",
            "{\"success\":true,\"data\":[{\"IntDocNumber\":null}]}",
            "{\"success\":true,\"data\":[{\"IntDocNumber\":\"invalid\"}]}"
    })
    void incompleteOrMistypedResponseRemainsUncertain(String response) {
        server.expect(requestTo("https://carrier.test/"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertEquals(DeliveryResult.uncertain(), client.send(recipient));
        server.verify();
    }

    @Test
    void ignoresAdditionalCarrierFieldsAndPreservesLeadingZeros() {
        server.expect(requestTo("https://carrier.test/"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"IntDocNumber":"00012345678901","Ref":"reference"}],
                         "errors":[],"warnings":[],"info":{"extra":true}}
                        """, MediaType.APPLICATION_JSON));
        assertEquals(DeliveryResult.accepted("00012345678901"), client.send(recipient));
        server.verify();
    }

}
