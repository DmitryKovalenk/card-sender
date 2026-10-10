package com.privatbank.cardsender.client;

import com.privatbank.cardsender.domain.DeliveryResult;
import com.privatbank.cardsender.domain.Recipient;

/**
 * Спочатку хотів назвати його TTNGatewat, але якось не звучить.
 * Тому використав WaybillGateway, бо він відповідає за відправку накладної (waybill) до перевізника.
 */
public interface WaybillGateway {

    DeliveryResult send(Recipient recipient);
}
