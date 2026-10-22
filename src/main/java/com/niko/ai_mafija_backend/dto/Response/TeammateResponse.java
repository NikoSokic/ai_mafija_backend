package com.niko.ai_mafija_backend.dto.Response;

/** Suigrač iz tima mafije – vidi ga samo tim mafije */
public record TeammateResponse(Long playerId, String nickname, String roleName, boolean alive) {
}
