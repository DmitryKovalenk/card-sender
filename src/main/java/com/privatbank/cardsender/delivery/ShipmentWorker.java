package com.privatbank.cardsender.delivery;

import com.privatbank.cardsender.service.ShipmentDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nova-poshta.enabled", havingValue = "true")
public class ShipmentWorker {
    private final ShipmentDispatcher dispatcher;
    private final int batchSize;

    public ShipmentWorker(ShipmentDispatcher dispatcher, NovaPoshtaProperties properties) {
        this.dispatcher = dispatcher;
        this.batchSize = properties.batchSize();
    }

    /**
     * Під час кожного запланованого опитування надсилаються відправлення, що очікують на відправку,
     * у межах налаштованого розміру пакету через batchSize
     */
    @Scheduled(fixedDelayString = "${nova-poshta.poll-delay-ms:10000}")
    public void process() {
        dispatcher.dispatchPending(batchSize);
    }
}
