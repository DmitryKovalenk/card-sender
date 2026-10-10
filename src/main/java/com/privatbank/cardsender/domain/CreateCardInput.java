package com.privatbank.cardsender.domain;

import com.privatbank.cardsender.dto.CardTypes;

public record CreateCardInput(String firstName, String lastName, CardTypes cardType) {
}
