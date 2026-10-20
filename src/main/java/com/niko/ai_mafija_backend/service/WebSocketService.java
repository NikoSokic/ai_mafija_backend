package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.WebSocket.LobbyEventResponse;
import com.niko.ai_mafija_backend.websocket.LobbyWebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final GameStateService gameStateService;

    public void publishLobbyEventAfterCommit(String lobbyCode, String type, String message, Object data) {
        eventPublisher.publishEvent(new LobbyWebSocketEvent(lobbyCode, type, message, data, false));
    }

    public void publishGameStateChangedAfterCommit(String lobbyCode) {
        eventPublisher.publishEvent(new LobbyWebSocketEvent(
                lobbyCode,
                "GAME_STATE_CHANGED",
                "Stanje igre je ažurirano.",
                null,
                true
        ));
    }

    public void publishGameFinishedAfterCommit(String lobbyCode) {
        eventPublisher.publishEvent(new LobbyWebSocketEvent(
                lobbyCode,
                "GAME_FINISHED",
                "Igra je završena.",
                null,
                true
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishLobbyEvent(LobbyWebSocketEvent event) {
        Object data = event.includePublicGameState()
                ? gameStateService.getPublicGameState(event.lobbyCode())
                : event.data();
        LobbyEventResponse response = new LobbyEventResponse(
                event.type(),
                event.message(),
                event.lobbyCode(),
                data
        );

        messagingTemplate.convertAndSend(
                "/topic/lobby/" + event.lobbyCode(),
                response
        );
    }
}
