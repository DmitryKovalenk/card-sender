package com.privatbank.cardsender.domain;

import java.util.Arrays;
import java.util.Optional;

public enum CardTypes {

    GOLD("444444"),
    SILVER("555555"),
    WOODEN("666666"),
    ELITE("000000");

    public final String bin;

    CardTypes(String bin) {
        this.bin = bin;
    }

    public static Optional<CardTypes> fromBin(String bin) {
        return Arrays.stream(values()).filter(type -> type.bin.equals(bin)).findFirst();
    }
}
