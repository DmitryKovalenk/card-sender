package com.privatbank.cardsender.config;

import com.privatbank.cardsender.client.NovaPoshtaClient;
import com.privatbank.cardsender.client.WaybillGateway;
import com.privatbank.cardsender.delivery.NovaPoshtaProperties;
import com.privatbank.cardsender.repository.ShipmentRepository;
import com.privatbank.cardsender.service.ShipmentDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NovaPoshtaProperties.class)
public class DeliveryConfiguration {
    /**
     * Створює dispatcher якщо Nova Poshta активована в пропертях.
     */
    @Bean
    @ConditionalOnProperty(name = "nova-poshta.enabled", havingValue = "true")
    ShipmentDispatcher shipmentDispatcher(ShipmentRepository shipments, WaybillGateway gateway) {
        return new ShipmentDispatcher(shipments, gateway);
    }

    /**
     * Створює клієнт для взаємодії з перевізником із налаштованими тайм-аутами HTTP та годинником,
     * що використовує місцевий час Києва.
     */
    @Bean
    @ConditionalOnProperty(name = "nova-poshta.enabled", havingValue = "true")
    WaybillGateway deliveryGateway(NovaPoshtaProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        // використав Europe/Kiev замість Europe/Kyiv щоб не було проблем при зборці з Maven та запуску тестів,
        // бо Maven може використовувати JDK < 17, а там немає Europe/Kyiv. Але Слава Україні, я за Kyiv.
        return new NovaPoshtaClient(properties, builder.requestFactory(factory).build(),
                Clock.system(ZoneId.of("Europe/Kiev")));
    }
}
