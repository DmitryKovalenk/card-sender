package com.privatbank.cardsender.service;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.repository.CardOrderRepository;
import com.privatbank.cardsender.repository.CardTypeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardService {
    private final CardTypeRepository cardTypeRepository;
    private final CardOrderRepository cardOrderRepository;
    private final EntityManager entityManager;

    public List<CardType> listTypes() {
        return cardTypeRepository.findAllByOrderByIdAsc();
    }

    /**
     * Створює замовлення на картку зі статусом «очікує виконання» після блокування та перевірки типу активної картки.
     */
    @Transactional
    public CardOrder create(CreateCardInput input, Operator operator) {
        CardType type = cardTypeRepository.findByBinForUpdate(input.cardType().bin)
                .filter(CardType::active)
                .orElseThrow(() -> new BusinessException(BusinessException.Kind.INVALID_REQUEST,
                        "Card type does not exist or is inactive"));
        return cardOrderRepository.saveAndFlush(
                CardOrder.pending(
                        input.firstName(),
                        input.lastName(),
                        type.id(),
                        operator
                )
        );
    }

    /**
     * Отримуємо існуюче замовлення на картку за ідентифікатором.
     */
    public CardOrder getOrder(long id) {
        return cardOrderRepository.findById(id).orElseThrow(() ->
                new BusinessException(BusinessException.Kind.NOT_FOUND, "Card order not found"));
    }

    /**
     * Створює номер картки для існуючого замовлення в транзакці,
     * повертаючи існуючий номер, якщо замовлення вже згенеровано.
     */
    @Transactional
    public String generateNumber(long orderId, Operator operator) {
        getOrder(orderId);
        entityManager.flush();
        String number = cardOrderRepository.generateNumber(orderId, operator.value());
        entityManager.clear();
        return number;
    }

    /**
     * Generates numbers in one transaction for pending orders that can be locked immediately.
     * Orders locked by concurrent transactions are skipped.
     * <p>
     * Створює номери карток в одній транзакції для замовлень, які очікують на обробку і можуть бути заблоковані негайно.
     */
    @Transactional
    public int generatePending(Operator operator) {
        entityManager.flush();
        List<Long> pendingIds = cardOrderRepository.lockPendingIds();
        for (long id : pendingIds) {
            cardOrderRepository.generateNumber(id, operator.value());
        }
        entityManager.clear();
        return pendingIds.size();
    }
}
