package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ResolveVoteResponse {

    /** ELIMINATED, TIE, NO_ELIMINATION ili pobjednik (MAFIA_WIN / CITIZENS_WIN) */
    private String result;
    private String message;
    private Integer roundNumber;

    private Long eliminatedPlayerId;
    private List<Long> tiedPlayerIds;

    /** Konačni broj glasova po igraču */
    private List<VoteCountResponse> voteCounts;
    /** Suzdržani + oni koji nisu glasali; null u ponovnom glasanju (TIE_BREAK) */
    private Integer abstainCount;
}
