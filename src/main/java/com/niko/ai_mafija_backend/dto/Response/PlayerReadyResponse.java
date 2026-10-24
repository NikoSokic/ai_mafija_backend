package com.niko.ai_mafija_backend.dto.Response;

/** Događaj PLAYER_READY (i odgovor na "Spreman sam"): tko je spreman i koliko je živih igrača spremno */
public record PlayerReadyResponse(Long playerId, int readyCount, int readyTotal) {
}
