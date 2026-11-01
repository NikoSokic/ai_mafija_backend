package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;

/** Zahtjev koji čeka domaćina (događaj RECLAIM_REQUESTED i polje reclaimRequests u odgovoru na heartbeat) */
public record ReclaimPendingResponse(
        Long requestId, Long playerId, String nickname, String avatarEmoji, OffsetDateTime expiresAt) {
}
