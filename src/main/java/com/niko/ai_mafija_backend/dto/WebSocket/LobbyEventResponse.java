package com.niko.ai_mafija_backend.dto.WebSocket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LobbyEventResponse {

    private String type;
    private String message;
    private String lobbyCode;
    private Object data;
}