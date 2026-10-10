package com.privatbank.cardsender.service.demo;

import com.privatbank.cardsender.domain.*;
import com.privatbank.cardsender.dto.CardTypes;
import com.privatbank.cardsender.service.CardService;
import com.privatbank.cardsender.service.ShipmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DemoDataService {
    private static final int MAX_RECORD_COUNT = 1000;
    private static final Operator DEMO_OPERATOR = new Operator("DEMO");

    private final CardService cards;
    private final ShipmentService shipments;
    private final DemoRecipientFactory recipients;

    public DemoDataService(CardService cards, ShipmentService shipments, DemoRecipientFactory recipients) {
        this.cards = cards;
        this.shipments = shipments;
        this.recipients = recipients;
    }

    @Transactional
    public DemoDataResult generate(int count) {
        if (count < 1 || count > MAX_RECORD_COUNT) {
            throw new BusinessException(BusinessException.Kind.INVALID_REQUEST,
                    "Count must be between 1 and " + MAX_RECORD_COUNT);
        }
        List<CardTypes> activeTypes = cards.listTypes().stream()
                .filter(CardType::active)
                .flatMap(type -> CardTypes.fromBin(type.bin()).stream())
                .toList();
        if (activeTypes.isEmpty()) {
            throw new BusinessException(BusinessException.Kind.CONFLICT, "No active supported card types found");
        }

        for (int index = 0; index < count; index++) {
            CardTypes type = activeTypes.get(index % activeTypes.size());
            var recipient = recipients.create(index);
            var command = new CreateCardInput(recipient.firstName(), recipient.lastName(), type);
            var card = cards.create(command, DEMO_OPERATOR);
            String number = cards.generateNumber(card.id(), DEMO_OPERATOR);
            shipments.create(number, recipient, DEMO_OPERATOR);
        }
        return new DemoDataResult(count, count);
    }
}
