package com.privatbank.cardsender.delivery;

import com.privatbank.cardsender.client.WaybillGateway;
import com.privatbank.cardsender.config.DeliveryConfiguration;
import com.privatbank.cardsender.repository.ShipmentRepository;
import com.privatbank.cardsender.service.ShipmentDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(DeliveryConfiguration.class, ShipmentWorker.class)
            .withBean(ShipmentRepository.class, () -> mock(ShipmentRepository.class));

    @Test
    void disabledDeliveryDoesNotRequireCredentialsOrCreateAWorker() {
        context.withPropertyValues("nova-poshta.enabled=false").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(WaybillGateway.class);
            assertThat(application).doesNotHaveBean(ShipmentWorker.class);
        });
    }

    @Test
    void enabledDeliveryWiresGatewayAndDispatcher() {
        context.withPropertyValues("nova-poshta.batch-size=7", "nova-poshta.enabled=true", "nova-poshta.api-key=test-key",
                        "nova-poshta.sender=sender", "nova-poshta.city-sender=city",
                        "nova-poshta.sender-warehouse-index=31/2", "nova-poshta.contact-sender=contact",
                        "nova-poshta.senders-phone=380671234567", "nova-poshta.url=https://carrier.test/")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).hasSingleBean(RestClient.Builder.class);
                    assertThat(application).hasSingleBean(WaybillGateway.class);
                    assertThat(application).hasSingleBean(ShipmentDispatcher.class);
                    assertThat(application).hasSingleBean(ShipmentWorker.class);
                    application.getBean(ShipmentWorker.class).process();
                    verify(application.getBean(ShipmentRepository.class)).findPending(7);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"nova-poshta.batch-size=0", "nova-poshta.batch-size=1001",
            "nova-poshta.connect-timeout=0s", "nova-poshta.read-timeout=-1s", "nova-poshta.url=ftp://carrier.test"})
    void invalidConfigurationFailsStartup(String property) {
        context.withPropertyValues("nova-poshta.enabled=false", property)
                .run(application -> assertThat(application).hasFailed());
    }

    @Test
    void enabledDeliveryRequiresCredentials() {
        context.withPropertyValues("nova-poshta.enabled=true")
                .run(application -> assertThat(application).hasFailed());
    }
}
