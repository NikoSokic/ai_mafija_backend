package com.niko.ai_mafija_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ActionRequest {

    private Long playerId;
    private Long targetPlayerId;
    private String actionType;
}