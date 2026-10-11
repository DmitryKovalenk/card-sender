package com.privatbank.cardsender.domain;

public record CreateCardInput(String firstName, String lastName, CardTypes cardType) {
}
