package com.niko.ai_mafija_backend.dto.Requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconnectRequest {

    private Long playerId;
    private String playerToken;
}