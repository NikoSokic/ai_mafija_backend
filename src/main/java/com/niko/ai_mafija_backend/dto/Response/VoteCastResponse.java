package com.niko.ai_mafija_backend.dto.Response;

/** Događaj VOTE_CAST: koliko je glasova predano, bez podatka tko je za koga glasao */
public record VoteCastResponse(int votedCount, int eligibleCount) {
}
