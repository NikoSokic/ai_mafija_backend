package com.niko.ai_mafija_backend.dto.Response;

import java.util.List;

/**
 * Javno glasanje uživo: tko smije glasati i tko je za koga glasao.
 * Postoji samo dok je glasanje otvoreno (VOTING / TIE_BREAK).
 */
public record PublicVotingResponse(
        List<Long> eligibleVoterIds,
        List<VoteEntryResponse> votes,
        List<Long> tiedPlayerIds
) {
}
