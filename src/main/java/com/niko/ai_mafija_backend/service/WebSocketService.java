package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.WebSocket.LobbyEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendLobbyEvent(String lobbyCode, String type, String message, Object data) {
        LobbyEventResponse response = new LobbyEventResponse(
                type,
                message,
                lobbyCode,
                data
        );

        messagingTemplate.convertAndSend(
                "/topic/lobby/" + lobbyCode,
                response
        );
    }
}