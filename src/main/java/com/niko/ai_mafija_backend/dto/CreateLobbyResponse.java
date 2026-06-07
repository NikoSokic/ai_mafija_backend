package com.niko.ai_mafija_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CreateLobbyResponse {

    private Long lobbyId;
    private String code;
    private String status;
    private Integer maxPlayers;

    private Long hostPlayerId;
    private String hostNickname;
}