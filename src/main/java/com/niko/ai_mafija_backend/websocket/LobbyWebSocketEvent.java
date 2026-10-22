package com.niko.ai_mafija_backend.websocket;

public record LobbyWebSocketEvent(
        String lobbyCode,
        String type,
        String message,
        Object data,
        boolean includePublicGameState
) {
}
