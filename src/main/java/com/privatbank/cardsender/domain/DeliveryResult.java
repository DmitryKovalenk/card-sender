package com.privatbank.cardsender.domain;

public record DeliveryResult(Outcome outcome, String waybill) {

    public DeliveryResult {
        if (outcome == null || (outcome == Outcome.ACCEPTED && (waybill == null || !waybill.matches("[0-9]{1,30}")))) {
            throw new IllegalArgumentException("An accepted delivery must include a valid waybill");
        }
        if (outcome != Outcome.ACCEPTED && waybill != null) {
            throw new IllegalArgumentException("Only accepted deliveries can include a waybill");
        }
    }

    public static DeliveryResult accepted(String waybill) {
        return new DeliveryResult(Outcome.ACCEPTED, waybill);
    }

    public static DeliveryResult rejected() {
        return new DeliveryResult(Outcome.REJECTED, null);
    }

    public static DeliveryResult uncertain() {
        return new DeliveryResult(Outcome.UNCERTAIN, null);
    }

    public enum Outcome {ACCEPTED, REJECTED, UNCERTAIN}
}
