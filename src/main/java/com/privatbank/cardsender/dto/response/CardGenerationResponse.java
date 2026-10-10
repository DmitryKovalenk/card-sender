package com.privatbank.cardsender.dto.response;

/**
 * Рекорд може здатися безтолковим, але він використовується для передачі кількості згенерованих карток у відповіді на запит.
 * Це краще ніж просто Integer, оскільки дозволяє розширювати відповідь у майбутньому без зміни контракту.
 */
public record CardGenerationResponse(Integer generated) {
}
