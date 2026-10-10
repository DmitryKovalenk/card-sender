package com.privatbank.cardsender.repository;

import com.privatbank.cardsender.domain.ShipmentOrder;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<ShipmentOrder, Long> {
    Optional<ShipmentOrder> findByCardOrderId(long cardOrderId);

    List<ShipmentOrder> findByStatusOrderByIdAsc(ShipmentOrder.Status status, Limit limit);

    default List<ShipmentOrder> findPending(int limit) {
        return findByStatusOrderByIdAsc(ShipmentOrder.Status.PENDING, Limit.of(limit));
    }

    default boolean claim(long id) {
        return claimPending(id) == 1;
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            UPDATE shipment_orders SET status = 'PROCESSING', updated_by = 'SHIPMENT_WORKER', updated_at = SYSTIMESTAMP
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int claimPending(@Param("id") long id);

    default void complete(long id, ShipmentOrder.Status status, String waybill) {
        if (status != ShipmentOrder.Status.SENT && status != ShipmentOrder.Status.FAILED) {
            throw new IllegalArgumentException("Completion status must be SENT or FAILED");
        }
        if (completeProcessing(id, status.name(), waybill) != 1) {
            throw new IllegalStateException("Shipment " + id + " is no longer processing");
        }
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            UPDATE shipment_orders SET status = :status, waybill_number = :waybill,
                updated_by = 'SHIPMENT_WORKER', updated_at = SYSTIMESTAMP
            WHERE id = :id AND status = 'PROCESSING'
            """, nativeQuery = true)
    int completeProcessing(@Param("id") long id, @Param("status") String status, @Param("waybill") String waybill);
}
