package com.privatbank.cardsender.dto.request;

import com.privatbank.cardsender.domain.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShipmentRequest(
        @Schema(example = "Дмитро")
        @NotBlank @Size(max = 100) String firstName,
        @Schema(example = "Коваленко")
        @NotBlank @Size(max = 100) String lastName,
        @Schema(description = "Замініть номер картки на існуючий номер, який був згенерований.",
                example = "4444440000000008")
        @NotNull @Pattern(regexp = "[0-9]{16}") String cardNumber,
        @Schema(description = "Номер отримувача", example = "380671234567")
        @NotNull @Pattern(regexp = "380[0-9]{9}") String phone,
        @Schema(example = "Дніпро")
        @NotBlank @Size(max = 40) String city,
        @Schema(description = "Номер склада Нової Пошти", example = "1")
        @NotBlank @Size(max = 30) String warehouse) {

    /**
     * Екстрактимо контактні та адресні дані отримувача із запиту на відправку.
     */
    public Recipient recipient() {
        return new Recipient(firstName, lastName, phone, city, warehouse);
    }
}
