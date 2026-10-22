package com.niko.ai_mafija_backend.exception;

import lombok.Getter;

/**
 * Zajednička iznimka API-ja: uz poruku nosi stabilan kod (ErrorCode) koji klijent može
 * uspoređivati umjesto teksta poruke.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;

    protected ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
