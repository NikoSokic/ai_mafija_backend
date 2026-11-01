package com.niko.ai_mafija_backend.dto.Response;

import java.time.OffsetDateTime;

/** claimSecret zna samo uređaj koji je stvorio zahtjev; njime dohvaća ishod i novi token */
public record ReclaimCreatedResponse(Long requestId, String claimSecret, OffsetDateTime expiresAt) {
}
