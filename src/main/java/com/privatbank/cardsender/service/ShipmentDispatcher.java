package com.privatbank.cardsender.service;

import com.privatbank.cardsender.client.WaybillGateway;
import com.privatbank.cardsender.domain.DeliveryResult;
import com.privatbank.cardsender.domain.ShipmentOrder;
import com.privatbank.cardsender.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class ShipmentDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ShipmentDispatcher.class);
    private final ShipmentRepository shipments;
    private final WaybillGateway gateway;

    public void dispatchPending(int limit) {
        for (ShipmentOrder shipment : shipments.findPending(limit)) {
            try {
                dispatch(shipment);
            } catch (RuntimeException exception) {
                // Продовжити пакетну обробку;
                // прийняті замовлення залишаються зі статусом «В обробці» (PROCESSING), а не надсилаються повторно.
                LOG.error("Shipment {} processing failed ({})", shipment.id(), exception.getClass().getSimpleName());
            }
        }
    }

    private void dispatch(ShipmentOrder shipment) {
        if (!shipments.claim(shipment.id())) {
            return;
        }
        DeliveryResult result = gateway.send(shipment.recipient());
        switch (result.outcome()) {
            case ACCEPTED -> shipments.complete(shipment.id(), ShipmentOrder.Status.SENT, result.waybill());
            case REJECTED -> shipments.complete(shipment.id(), ShipmentOrder.Status.FAILED, null);
            case UNCERTAIN -> LOG.warn("Shipment {} outcome uncertain; reconcile with Nova Poshta before resubmitting",
                    shipment.id());
        }
    }
}
