package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ActionResponse {

    private Long actionId;
    private String actionType;
    private Long playerId;
    private Long targetPlayerId;
    private String message;

    private String investigationResult;
    private String journalistResult;

    private boolean nightResolved;
    private ResolveNightResponse resolveResult;
}