package com.privatbank.cardsender.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record Recipient(String firstName, String lastName, String phone, String city, String warehouse) {
}
