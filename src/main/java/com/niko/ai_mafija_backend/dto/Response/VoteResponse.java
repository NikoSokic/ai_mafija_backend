package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class VoteResponse {

    private Long voteId;
    private Long voterPlayerId;
    private Long targetPlayerId;
    private String message;

    private boolean votingResolved;
    private ResolveVoteResponse resolveResult;
}