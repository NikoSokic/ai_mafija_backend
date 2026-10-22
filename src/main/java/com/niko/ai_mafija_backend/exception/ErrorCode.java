package com.niko.ai_mafija_backend.exception;

/**
 * Stabilni kodovi grešaka u ApiErrorResponse.code – tekst poruke se smije mijenjati, kod ne.
 * Frontend (src/api/rest.ts) prevodi poruke po imenu koda, pa se postojeći kodovi ne preimenuju.
 */
public enum ErrorCode {

    // općenito
    BAD_REQUEST,
    VALIDATION_ERROR,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR,

    // sobe i igrači
    LOBBY_NOT_FOUND,
    LOBBY_FULL,
    LOBBY_NOT_FULL,
    NICKNAME_TAKEN,
    GAME_ALREADY_STARTED,
    PLAYER_NOT_FOUND,
    NOT_IN_LOBBY,
    NOT_HOST,
    TOKEN_REQUIRED,
    INVALID_TOKEN,
    ROLE_NOT_FOUND,
    ROLE_NOT_ASSIGNED,
    INVALID_ROLE_CONFIG,
    GAME_IN_PROGRESS,

    // tijek igre
    GAME_NOT_STARTED,
    GAME_FINISHED,
    WRONG_PHASE,
    DEAD_PLAYER,

    // noć
    NOT_YOUR_TURN,
    INVALID_ACTION,
    INVALID_TARGET,
    ALREADY_ACTED,

    // glasanje
    /** Općenito "ne smiješ glasati", kad nema preciznijeg koda ispod */
    CANNOT_VOTE,
    SILENCED,
    TIED_PLAYER_CANNOT_VOTE,
    ALREADY_VOTED,
    CANNOT_VOTE_SELF,
    ABSTAIN_NOT_ALLOWED
}
