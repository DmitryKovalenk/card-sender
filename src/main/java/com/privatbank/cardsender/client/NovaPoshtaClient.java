package com.privatbank.cardsender.client;

import com.privatbank.cardsender.client.novaposhta.dto.CreateWaybillRequest;
import com.privatbank.cardsender.client.novaposhta.dto.CreateWaybillResponse;
import com.privatbank.cardsender.client.novaposhta.dto.NovaPoshtaRequest;
import com.privatbank.cardsender.delivery.NovaPoshtaProperties;
import com.privatbank.cardsender.domain.DeliveryResult;
import com.privatbank.cardsender.domain.Recipient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.LocalDate;

@RequiredArgsConstructor
public class NovaPoshtaClient implements WaybillGateway {
    private final NovaPoshtaProperties properties;
    private final RestClient client;
    private final Clock clock;

    /**
     * Створюємо waybill для відправки картки, якщо все ок, то повертаємо accepted із номером накладної,
     * якщо ні, то rejected, якщо не впевнені, то uncertain.
     */
    @Override
    public DeliveryResult send(Recipient recipient) {
        try {
            CreateWaybillResponse response = client
                    .post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(recipient))
                    .retrieve()
                    .body(CreateWaybillResponse.class);
            return parse(response);
        } catch (RestClientException exception) {
            return DeliveryResult.uncertain();
        }
    }

    /**
     * Готуємо запит на створення накладної з конфігурацією відправника, деталями отримувача та фіксованими
     * параметрами відправки документів.
     */
    private NovaPoshtaRequest<CreateWaybillRequest> payload(Recipient recipient) {
        CreateWaybillRequest waybill = CreateWaybillRequest.from(properties, recipient, LocalDate.now(clock));
        return new NovaPoshtaRequest<>(properties.apiKey(), "InternetDocument", "save", waybill);
    }

    /**
     * Інтерпретує явну помилку як відмову та успішну відповідь із дійсною накладною як прийняття.
     * Відсутність або неправильне підтвердження залишається невизначеним.
     */
    private static DeliveryResult parse(CreateWaybillResponse response) {
        if (response == null || response.success() == null) {
            return DeliveryResult.uncertain();
        }
        if (!response.success()) {
            return DeliveryResult.rejected();
        }
        if (response.data() == null || response.data().isEmpty() || response.data().get(0) == null) {
            return DeliveryResult.uncertain();
        }
        String waybill = response.data().get(0).number();
        return waybill != null && waybill.matches("[0-9]{1,30}")
                ? DeliveryResult.accepted(waybill) : DeliveryResult.uncertain();
    }
}
