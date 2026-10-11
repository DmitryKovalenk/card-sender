package com.privatbank.cardsender.client;

import com.privatbank.cardsender.client.model.CreateWaybillRequest;
import com.privatbank.cardsender.client.model.CreateWaybillResponse;
import com.privatbank.cardsender.client.model.NovaPoshtaRequest;
import com.privatbank.cardsender.delivery.NovaPoshtaProperties;
import com.privatbank.cardsender.domain.DeliveryResult;
import com.privatbank.cardsender.domain.Recipient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.LocalDate;

@RequiredArgsConstructor
public class NovaPoshtaClient implements WaybillGateway {
    private static final Logger LOG = LoggerFactory.getLogger(NovaPoshtaClient.class);
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
        } catch (RestClientResponseException exception) {
            diagnostic("HTTP_ERROR", "status=" + exception.getStatusCode().value());
            return DeliveryResult.uncertain();
        } catch (ResourceAccessException exception) {
            diagnostic("TRANSPORT_ERROR", "cause=" + (exception.getCause() == null
                    ? exception.getClass().getSimpleName() : exception.getCause().getClass().getSimpleName()));
            return DeliveryResult.uncertain();
        } catch (RestClientException exception) {
            diagnostic("RESPONSE_ERROR", "type=" + exception.getClass().getSimpleName());
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
    private DeliveryResult parse(CreateWaybillResponse response) {
        if (response == null || response.success() == null) {
            diagnostic("INVALID_RESPONSE", "Missing success flag");
            return DeliveryResult.uncertain();
        }
        if (!response.success()) {
            diagnostic("REJECTED", String.valueOf(response.errors()));
            return DeliveryResult.rejected();
        }
        if (response.data() == null || response.data().isEmpty() || response.data().get(0) == null) {
            diagnostic("INVALID_RESPONSE", "Missing waybill data");
            return DeliveryResult.uncertain();
        }
        String waybill = response.data().get(0).number();
        if (waybill != null && waybill.matches("[0-9]{1,30}")) {
            return DeliveryResult.accepted(waybill);
        }
        diagnostic("INVALID_RESPONSE", "Missing or invalid waybill number");
        return DeliveryResult.uncertain();
    }

    private static void diagnostic(String reason, String detail) {
        LOG.warn("Nova Poshta reason={} detail={}", reason, detail);
    }
}
