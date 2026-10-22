package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/** Što se dogodi kad istekne rok trenutne faze ili poteza. Poziva ga GameTimerService. */
@Service
@RequiredArgsConstructor
public class GamePhaseService {

    private final GameStateRepository gameStateRepository;
    private final ActionService actionService;
    private final VoteService voteService;

    @Transactional
    public void handleTimeout(Long gameStateId) {
        GameState gameState = gameStateRepository.findByIdForUpdate(gameStateId).orElse(null);
        // u međuvremenu ga je možda već pomaknuo igrač ili domaćin
        if (gameState == null
                || gameState.getPhaseEndsAt() == null
                || gameState.getPhaseEndsAt().isAfter(OffsetDateTime.now())) {
            return;
        }

        Lobby lobby = gameState.getLobby();
        switch (gameState.getPhase()) {
            case NIGHT -> actionService.handleNightTimeout(lobby, gameState);
            case DAY -> voteService.startVoting(lobby, gameState);
            case VOTING_PHASE, TIE_BREAK -> voteService.resolveVote(lobby, gameState);
            default -> {
                gameState.setPhaseEndsAt(null);
                gameStateRepository.save(gameState);
            }
        }
    }
}
