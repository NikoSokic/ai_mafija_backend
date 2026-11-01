package com.niko.ai_mafija_backend.dto.Response;

/**
 * Ishod zahtjeva: PENDING, DENIED, EXPIRED, CLAIMED ili APPROVED. Samo uz APPROVED (jednokratno) stižu podaci
 * o igraču i novi playerToken; stari token prestaje vrijediti u tom trenutku.
 */
public record ReclaimStatusResponse(
        String status, Long playerId, String nickname, Boolean alive,
        Long lobbyId, String lobbyCode, String playerToken) {

    public static ReclaimStatusResponse of(String status) {
        return new ReclaimStatusResponse(status, null, null, null, null, null, null);
    }
}
