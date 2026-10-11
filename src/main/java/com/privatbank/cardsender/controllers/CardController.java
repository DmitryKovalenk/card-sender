package com.privatbank.cardsender.controllers;

import com.privatbank.cardsender.domain.Operator;
import com.privatbank.cardsender.dto.request.CardRequest;
import com.privatbank.cardsender.dto.response.CardGenerationResponse;
import com.privatbank.cardsender.dto.response.CardOrderResponse;
import com.privatbank.cardsender.dto.response.CardTypeResponse;
import com.privatbank.cardsender.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Картки", description = "Типи карток, замовлення, створення заявок та генерація номерів")
@RequiredArgsConstructor
public class CardController {

    private final CardService cards;

    /**
     * ендпоінт просто повертає список типів карток, які є в базі даних.
     */
    @GetMapping("/card-types")
    @Operation(
            summary = "Список типів карток",
            description = "Список типів карток та бінів; Список використовується для створення заявок на картки, " +
                          "та є свого роду довідником"
    )
    public List<CardTypeResponse> types() {
        return cards.listTypes()
                .stream()
                .map(CardTypeResponse::from)
                .toList();
    }

    /**
     * Тут ми створюємо заявку на картку, яка ще не має номера.
     * Також вирішив що тут більше підходить статус 201 Created, бо ми створюємо новий ресурс у базі даних.
     */
    @PostMapping("/card-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Стоверння заявки на картку",
            description = "Створює заявку без номера картки, імʼя та призвище обовʼязково мають містити латинські літери")
    public CardOrderResponse create(@Valid @RequestBody CardRequest request,
                                    @Parameter(
                                            description = "Оператор, який подав заявку, в ідеалі тут має бути ldap, ",
                                            example = "demo")
                                    @RequestHeader("X-Operator") String operator) {
        return CardOrderResponse.from(cards.create(request.toInput(), new Operator(operator)));
    }

    /**
     * Повертає заявку на картку за її ідентифікатором у поточному стані.
     */
    @GetMapping("/card-orders/{id}")
    @Operation(
            summary = "Отримати заявку на картку по її id",
            description = "Повертає заявку зі статусом PENDING або GENERATED. Для PENDING номер картки відсутній; " +
                          "для GENERATED відповідь містить згенерований номер картки."
    )
    public CardOrderResponse order(@PathVariable long id) {
        return CardOrderResponse.from(cards.getOrder(id));
    }

    /**
     * Генеруємо номера карток для всіх заявок, які мають статус pending і доступні для обробки.
     */
    @PostMapping("/card-orders/generate")
    @Operation(
            summary = "Генеруємо номера карток для всіх заявок, які очікують на номер картки",
            description = "Викликає процедуру Oracle для обробки очікуваних замовлень і повертає кількість оброблених записів. " +
                          "Кожен номер є унікальним і містить контрольну цифру за алгоритмом Луна."
    )
    public CardGenerationResponse generate(
            @Parameter(description = "Оператор, який подав заявку, в ідеалі тут теж має бути ldap", example = "demo")
            @RequestHeader(value = "X-Operator", defaultValue = "CARD_GENERATOR") String operator) {
        return new CardGenerationResponse(cards.generatePending(new Operator(operator)));
    }
}
