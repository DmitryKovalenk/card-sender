package com.privatbank.cardsender.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Entity
@Table(name = "card_orders")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CardOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "card_type_id")
    private long cardTypeId;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "card_number")
    private String cardNumber;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;
    @Embedded
    private Audit audit;

    public static CardOrder pending(String firstName, String lastName, long cardTypeId, Operator operator) {
        return new CardOrder(
                0,
                cardTypeId,
                firstName,
                lastName,
                null,
                Status.PENDING,
                null,
                Audit.createdBy(operator)
        );
    }

    public enum Status {PENDING, GENERATED}
}
