package com.niko.ai_mafija_backend.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor

public class ReconnectResponse {

    private Boolean valid;

    private Long playerId;
    private String nickname;
    private Boolean alive;
    private Boolean host;

    private Long lobbyId;
    private String lobbyCode;
    private String lobbyStatus;

    private String roleName;

    private String phase;
    private Integer roundNumber;
    private String currentTurnRole;
}