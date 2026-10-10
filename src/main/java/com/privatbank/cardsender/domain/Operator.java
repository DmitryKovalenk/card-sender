package com.privatbank.cardsender.domain;

public record Operator(String value) {
    /**
     * Валідація оператора. Я майже певен, що оператор буде ідентифікоавний по ldap, тому ще це поширено в Приваті.
     * Але цього в Т/З не було, тому я дам розгулятися на 20 символів і буду сподіватись, що туди все ж прийде Ldap :)
     */
    public Operator {
        if (value == null || value.isBlank() || value.length() > 20) {
            throw new BusinessException(BusinessException.Kind.INVALID_REQUEST,
                    "Operator must contain 1–20 characters");
        }
        value = value.trim();
    }
}
