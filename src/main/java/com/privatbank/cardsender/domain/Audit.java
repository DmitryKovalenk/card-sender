package com.privatbank.cardsender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

@Embeddable
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class Audit {
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;
    @Generated
    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;

    public static Audit createdBy(Operator operator) {
        return new Audit(operator.value(), null, operator.value(), null);
    }
}
