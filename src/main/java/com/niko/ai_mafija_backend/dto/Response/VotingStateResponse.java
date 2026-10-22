package com.niko.ai_mafija_backend.dto.Response;

import java.util.List;

/**
 * @param canAbstain              "Suzdržan" je dopušten samo u prvom glasanju (VOTING), ne u TIE_BREAK
 * @param eligibleTargetPlayerIds za koga ovaj igrač smije glasati (bez sebe)
 * @param eligibleVoterIds        tko u ovom glasanju smije glasati
 * @param votes                   glasovi uživo (glasanje je otvoreno)
 */
public record VotingStateResponse(
        boolean votingOpen,
        boolean canVote,
        boolean hasVoted,
        boolean canAbstain,
        List<Long> eligibleTargetPlayerIds,
        List<Long> tiedPlayerIds,
        List<Long> eligibleVoterIds,
        List<VoteEntryResponse> votes
) {
}
