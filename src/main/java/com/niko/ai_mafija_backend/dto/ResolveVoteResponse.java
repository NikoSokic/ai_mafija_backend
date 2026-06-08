package com.niko.ai_mafija_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ResolveVoteResponse {

    private String result;
    private String message;
    private Integer roundNumber;

    private Long eliminatedPlayerId;
    private List<Long> tiedPlayerIds;
}