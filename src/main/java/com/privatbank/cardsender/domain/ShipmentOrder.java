package com.privatbank.cardsender.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "shipment_orders")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShipmentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "card_order_id")
    private long cardOrderId;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "firstName", column = @Column(name = "recipient_first_name")),
            @AttributeOverride(name = "lastName", column = @Column(name = "recipient_last_name")),
            @AttributeOverride(name = "phone", column = @Column(name = "recipient_phone")),
            @AttributeOverride(name = "city", column = @Column(name = "recipient_city")),
            @AttributeOverride(name = "warehouse", column = @Column(name = "warehouse_number"))
    })
    private Recipient recipient;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "waybill_number")
    private String waybill;
    @Embedded
    private Audit audit;

    public enum Status {PENDING, PROCESSING, SENT, FAILED}

    public static ShipmentOrder pending(long cardOrderId, Recipient recipient, Operator operator) {
        return new ShipmentOrder(0, cardOrderId, recipient, Status.PENDING, null, Audit.createdBy(operator));
    }
}
