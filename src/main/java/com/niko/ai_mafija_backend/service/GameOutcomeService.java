package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameOutcomeService {

    public static final String WINNER_PREFIX = "WINNER:";

    private final PlayerRepository playerRepository;
    private final LobbyRepository lobbyRepository;
    private final GameStateRepository gameStateRepository;
    private final WebSocketService webSocketService;

    public String finishGameIfWinnerExists(Lobby lobby, GameState gameState) {
        String winner = determineWinner(lobby);
        if (winner == null) {
            return null;
        }

        lobby.setStatus("FINISHED");
        lobby.setEndedAt(OffsetDateTime.now());
        gameState.setPhase("FINISHED");
        gameState.setCurrentTurnRole(null);
        gameState.setPhaseEndsAt(null);
        gameState.setTieBreakPlayerIds(WINNER_PREFIX + winner);
        gameState.setUpdateAt(OffsetDateTime.now());

        lobbyRepository.save(lobby);
        gameStateRepository.save(gameState);
        webSocketService.publishGameFinishedAfterCommit(lobby.getCode());
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

        return winner;
    }

    /** Igrač ispada: how je GameRules.ELIMINATED_NIGHT / _VOTE / _LEFT */
    public void eliminate(Player player, String how, Integer roundNumber) {
        player.setIsAlive(false);
        player.setEliminatedHow(how);
        player.setEliminatedRound(roundNumber);
        playerRepository.save(player);
    }

    public String determineWinner(Lobby lobby) {
        List<Player> alivePlayers = playerRepository.findByLobbyAndIsAliveTrue(lobby);
        long mafiaTeamCount = alivePlayers.stream()
                .filter(player -> player.getRole() != null)
                .filter(player -> "Mafia".equalsIgnoreCase(player.getRole().getName())
                        || "Dama".equalsIgnoreCase(player.getRole().getName()))
                .count();
        long otherPlayersCount = alivePlayers.size() - mafiaTeamCount;

        if (mafiaTeamCount == 0) {
            return "CITIZENS_WIN";
        }
        if (mafiaTeamCount >= otherPlayersCount) {
            return "MAFIA_WIN";
        }
        return null;
    }

    public static String getStoredWinner(GameState gameState) {
        if (gameState == null || gameState.getTieBreakPlayerIds() == null) {
            return null;
        }
        String value = gameState.getTieBreakPlayerIds();
        return value.startsWith(WINNER_PREFIX) ? value.substring(WINNER_PREFIX.length()) : null;
    }
}
