package com.privatbank.cardsender.controllers;

import com.privatbank.cardsender.domain.BusinessException;
import com.privatbank.cardsender.domain.Operator;
import com.privatbank.cardsender.dto.request.ShipmentRequest;
import com.privatbank.cardsender.dto.response.OrderStatusResponse;
import com.privatbank.cardsender.dto.response.ShipmentOrderResponse;
import com.privatbank.cardsender.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Відправка карток", description = "Запит на відправку картки у Нову Пошту та перевірка статусу заявки")
public class ShipmentController {

    private final ShipmentService shipments;

    /**
     * Створює заявку на відправку картки у Нову Пошту, яка буде оброблена шедулером.
     */
    @PostMapping("/shipment-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Замовити доставку картки",
            description = "Додає в чергу відправлення для вже створеної картки. " +
                          "Використовуйте власні дані отримувача. " +
                          "Шедуллер надсилає запит, коли активовано nova-poshta.enabled в пропертях")
    public ShipmentOrderResponse create(@Valid @RequestBody ShipmentRequest request,
                                        @Parameter(description = "ldap того, хто ініціює заявку", example = "demo")
                                        @RequestHeader("X-Operator") String operator) {
        return ShipmentOrderResponse.from(
                shipments.create(request.cardNumber(), request.recipient(), new Operator(operator))
        );
    }

    /**
     * Тут ми можемо перевірити статус заявки на картку і відправки її у Нову Пошту.
     * Якщо заявка на картку ще не була створена, то буде помилка.
     */
    @GetMapping("/orders/status/{cardNumber}")
    @Operation(summary = "Отримати статус заявки на картку і відправки її у Нову Пошту",
            description = "Повертає значення CARD_GENERATED до моменту запиту на відправлення; " +
                          "в інших випадках — PENDING, PROCESSING, SENT або FAILED. " +
                          "Статус SENT означає, що було створено накладну, а не те, що посилку доставлено. " +
                          "Якщо результат дій перевізника невизначений, статус залишається PROCESSING для подальшої звірки.")
    public OrderStatusResponse status(
            @Parameter(description = "The generated 16-digit card number", example = "4444440000000008")
            @PathVariable String cardNumber) {
        if (!cardNumber.matches("[0-9]{16}")) {
            throw new BusinessException(BusinessException.Kind.INVALID_REQUEST, "Expected a 16-digit card number");
        }
        return OrderStatusResponse.from(shipments.getStatus(cardNumber));
    }
}
