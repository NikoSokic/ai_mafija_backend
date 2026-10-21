package com.niko.ai_mafija_backend.dto.Requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoteRequest {

    private Long voterPlayerId;
    /** null = "Suzdržan" (samo u prvom glasanju) */
    private Long targetPlayerId;

    private String playerToken;
}
