package com.niko.ai_mafija_backend.dto.Response;

/** Podaci događaja PLAYER_KICKED: izbačeni igrač to vidi po svom playerId */
public record PlayerKickedResponse(Long playerId, String nickname) {
}
