package com.privatbank.cardsender.dto.request;

import com.privatbank.cardsender.domain.CreateCardInput;
import com.privatbank.cardsender.domain.CardTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CardRequest(
        @Schema(description = "Імʼя власника кртки (тільки латинські літери)", example = "Dmytro")
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z]+(?:[ '-][A-Za-z]+)*") String firstName,
        @Schema(description = "Прізвище власника кртки (тільки латинські літери)", example = "Kovalenko")
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z]+(?:[ '-][A-Za-z]+)*") String lastName,
        @Schema(description = "Код типу картки. Для зручності це буде шось накшталт ELITE, SILVER, WOODEN, GOLD",
                example = "GOLD")
        @NotNull CardTypes cardType) {

    /**
     * Мапає перевірений HTTP-запит із вхідними даними, які приймає сервіс обробки карток.
     */
    public CreateCardInput toInput() {
        return new CreateCardInput(firstName, lastName, cardType);
    }
}
