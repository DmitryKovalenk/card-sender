package com.privatbank.cardsender.domain;

public class BusinessException extends RuntimeException {
    public enum Kind {INVALID_REQUEST, NOT_FOUND, CONFLICT}

    private final Kind kind;

    public BusinessException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
