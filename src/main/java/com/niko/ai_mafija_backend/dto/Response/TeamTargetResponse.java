package com.niko.ai_mafija_backend.dto.Response;

/** Koga je član tima mafije noćas odabrao za KILL – vidi samo tim mafije */
public record TeamTargetResponse(Long playerId, Long targetPlayerId) {
}
