package com.privatbank.cardsender.dto.response;

import com.privatbank.cardsender.domain.DemoDataResult;

public record DemoDataResponse(int cardOrdersCreated, int shipmentOrdersCreated) {

    public static DemoDataResponse from(DemoDataResult result) {
        return new DemoDataResponse(result.cardOrdersCreated(), result.shipmentOrdersCreated());
    }
}
