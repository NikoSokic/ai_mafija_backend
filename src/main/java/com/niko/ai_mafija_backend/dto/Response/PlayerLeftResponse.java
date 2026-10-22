package com.niko.ai_mafija_backend.dto.Response;

/** Podaci događaja PLAYER_LEFT; newHostPlayerId je postavljen samo ako je otišao domaćin */
public record PlayerLeftResponse(Long playerId, String nickname, Long newHostPlayerId) {
}
