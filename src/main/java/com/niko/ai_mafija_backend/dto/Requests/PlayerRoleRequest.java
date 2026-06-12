package com.niko.ai_mafija_backend.dto.Requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerRoleRequest {

    private Long playerId;
    private String playerToken;
}