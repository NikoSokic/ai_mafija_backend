package com.niko.ai_mafija_backend.dto.Response;

/** Podaci događaja RECLAIM_RESOLVED: status je APPROVED ili DENIED */
public record ReclaimResolvedResponse(Long requestId, String status) {
}
