package com.privatbank.cardsender.repository;

import com.privatbank.cardsender.domain.CardOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CardOrderRepository extends JpaRepository<CardOrder, Long> {
    Optional<CardOrder> findByCardNumber(String number);

    @Query(value = "SELECT id FROM card_orders WHERE status = 'PENDING' ORDER BY id FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Long> lockPendingIds();

    @Transactional
    @Procedure(procedureName = "generate_card_number", outputParameterName = "p_card_number")
    String generateNumber(@Param("p_card_order_id") long orderId, @Param("p_operator") String operator);
}
