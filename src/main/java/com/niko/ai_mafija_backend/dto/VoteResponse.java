package com.niko.ai_mafija_backend.dto;

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
}