package com.niko.ai_mafija_backend.dto.Response;

/** Jedan glas; targetPlayerId je null za "Suzdržan" */
public record VoteEntryResponse(Long voterPlayerId, Long targetPlayerId) {
}
