package com.privatbank.cardsender.service;

import com.privatbank.cardsender.client.WaybillGateway;
import com.privatbank.cardsender.domain.DeliveryResult;
import com.privatbank.cardsender.domain.Recipient;
import com.privatbank.cardsender.domain.ShipmentOrder;
import com.privatbank.cardsender.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class ShipmentDispatcherTests {
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final WaybillGateway gateway = mock(WaybillGateway.class);
    private final ShipmentDispatcher dispatcher = new ShipmentDispatcher(shipments, gateway);
    private final Recipient recipient = new Recipient("Test", "User", "380671234567", "Київ", "1");
    private final ShipmentOrder order = new ShipmentOrder(1, 1, recipient, ShipmentOrder.Status.PENDING, null, null);

    @Test
    void losingAClaimDoesNotCallCarrier() {
        when(shipments.findPending(20)).thenReturn(List.of(order));
        when(shipments.claim(1)).thenReturn(false);
        dispatcher.dispatchPending(20);
        verifyNoInteractions(gateway);
    }

    @Test
    void uncertaintyKeepsTheClaimForReconciliation() {
        when(shipments.findPending(20)).thenReturn(List.of(order));
        when(shipments.claim(1)).thenReturn(true);
        when(gateway.send(recipient)).thenReturn(DeliveryResult.uncertain());
        dispatcher.dispatchPending(20);
        verify(shipments, never()).complete(anyLong(), any(), any());
    }

    @Test
    void unexpectedFailureDoesNotStopTheRemainingBatch() {
        var second = new ShipmentOrder(2, 2, recipient, ShipmentOrder.Status.PENDING, null, null);
        when(shipments.findPending(20)).thenReturn(List.of(order, second));
        when(shipments.claim(anyLong())).thenReturn(true);
        when(gateway.send(recipient)).thenThrow(new IllegalStateException("unexpected"))
                .thenReturn(DeliveryResult.accepted("20400000000001"));
        dispatcher.dispatchPending(20);
        verify(shipments).complete(2, ShipmentOrder.Status.SENT, "20400000000001");
        verify(shipments, never()).complete(eq(1L), any(), any());
    }

    @Test
    void rejectionCompletesAsFailedWithoutWaybill() {
        when(shipments.findPending(20)).thenReturn(List.of(order));
        when(shipments.claim(1)).thenReturn(true);
        when(gateway.send(recipient)).thenReturn(DeliveryResult.rejected());
        dispatcher.dispatchPending(20);
        verify(shipments).complete(1, ShipmentOrder.Status.FAILED, null);
    }

    @Test
    void completionFailureDoesNotResendOrStopRemainingOrders() {
        var second = new ShipmentOrder(2, 2, recipient, ShipmentOrder.Status.PENDING, null, null);
        when(shipments.findPending(20)).thenReturn(List.of(order, second));
        when(shipments.claim(anyLong())).thenReturn(true);
        when(gateway.send(recipient)).thenReturn(DeliveryResult.accepted("00123"));
        doThrow(new IllegalStateException("database failure")).when(shipments)
                .complete(1, ShipmentOrder.Status.SENT, "00123");
        dispatcher.dispatchPending(20);
        verify(gateway, times(2)).send(recipient);
        verify(shipments).complete(2, ShipmentOrder.Status.SENT, "00123");
    }
}
