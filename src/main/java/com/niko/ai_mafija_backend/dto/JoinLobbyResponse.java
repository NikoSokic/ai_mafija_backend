package com.niko.ai_mafija_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class JoinLobbyResponse {

    private Long playerId;
    private String nickname;
    private Boolean alive;

    private Long lobbyId;
    private String lobbyCode;
}