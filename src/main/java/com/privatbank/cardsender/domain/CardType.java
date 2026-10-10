package com.privatbank.cardsender.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.type.NumericBooleanConverter;

@Entity
@Table(name = "card_types")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CardType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;
    private String bin;
    @Column(name = "is_active")
    @Convert(converter = NumericBooleanConverter.class)
    private boolean active;
    @Embedded
    private Audit audit;
}
