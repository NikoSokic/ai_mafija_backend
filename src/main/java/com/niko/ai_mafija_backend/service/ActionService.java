package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.ActionRequest;
import com.niko.ai_mafija_backend.dto.ActionResponse;
import com.niko.ai_mafija_backend.dto.ResolveNightResponse;
import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.niko.ai_mafija_backend.dto.ResolveNightResponse;
import com.niko.ai_mafija_backend.model.Lobby;

import java.util.List;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ActionService {

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final GameActionRepository gameActionRepository;

    public ActionResponse submitAction(ActionRequest request) {

        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new RuntimeException("Igrač ne postoji."));

        Player targetPlayer = playerRepository.findById(request.getTargetPlayerId())
                .orElseThrow(() -> new RuntimeException("Meta ne postoji."));

        if (!player.getLobby().getId().equals(targetPlayer.getLobby().getId())) {
            throw new RuntimeException("Igrač i meta nisu u istom lobbyju.");
        }

        if (!player.getIsAlive()) {
            throw new RuntimeException("Mrtav igrač ne može izvršiti akciju.");
        }

        if (!targetPlayer.getIsAlive()) {
            throw new RuntimeException("Ne možeš ciljati mrtvog igrača.");
        }

        GameState gameState = gameStateRepository.findByLobby(player.getLobby())
                .orElseThrow(() -> new RuntimeException("Game state ne postoji."));

        if (!gameState.getPhase().equals("NIGHT")) {
            throw new RuntimeException("Akcije se mogu slati samo tijekom noći.");
        }

        validateRoleAction(player, request.getActionType());

        boolean actionAlreadyExists = gameActionRepository.existsByPlayerAndPhaseAndRoundNumber(
                player,
                gameState.getPhase(),
                gameState.getRoundNumber()
        );

        if (actionAlreadyExists) {
            throw new RuntimeException("Već si odigrao potez u ovoj fazi.");
        }

        if (gameState.getPhase().equals("FINISHED")) {
            throw new RuntimeException("Igra je završena.");
        }

        GameAction action = GameAction.builder()
                .player(player)
                .targetPlayer(targetPlayer)
                .lobby(player.getLobby())
                .actionType(request.getActionType())
                .phase(gameState.getPhase())
                .roundNumber(gameState.getRoundNumber())
                .createdAt(OffsetDateTime.now())
                .build();

        GameAction savedAction = gameActionRepository.save(action);

        return new ActionResponse(
                savedAction.getId(),
                savedAction.getActionType(),
                savedAction.getPlayer().getId(),
                savedAction.getTargetPlayer().getId(),
                "Akcija je spremljena."
        );
    }
    private void validateRoleAction(Player player, String actionType) {

        if (player.getRole() == null) {
            throw new RuntimeException("Igrač nema dodijeljenu ulogu.");
        }

        String roleName = player.getRole().getName();

        switch (roleName) {
            case "Mafia" -> {
                if (!actionType.equals("KILL")) {
                    throw new RuntimeException("Mafia može koristiti samo KILL akciju.");
                }
            }

            case "Doktor" -> {
                if (!actionType.equals("SAVE")) {
                    throw new RuntimeException("Doktor može koristiti samo SAVE akciju.");
                }
            }

            case "Policajac" -> {
                if (!actionType.equals("INVESTIGATE")) {
                    throw new RuntimeException("Policajac može koristiti samo INVESTIGATE akciju.");
                }
            }

            case "Dama" -> {

                boolean aliveMafiaExists = hasAliveMafia(player.getLobby());

                if (aliveMafiaExists) {

                    if (!actionType.equals("SILENCE")) {
                        throw new RuntimeException(
                                "Dama može koristiti samo SILENCE dok je Mafia živa."
                        );
                    }

                } else {

                    if (!actionType.equals("KILL")) {
                        throw new RuntimeException(
                                "Dama preuzima KILL kada nema žive Mafije."
                        );
                    }
                }
            }

            case "Građanin" -> {
                throw new RuntimeException("Građanin nema noćnu akciju.");
            }

            default -> throw new RuntimeException("Nepoznata uloga: " + roleName);
        }
    }
    public ResolveNightResponse resolveNight(String lobbyCode) {

        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new RuntimeException("Lobby ne postoji."));

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElseThrow(() -> new RuntimeException("Game state ne postoji."));

        if (gameState.getPhase().equals("FINISHED")) {
            throw new RuntimeException("Igra je završena.");
        }

        if (!gameState.getPhase().equals("NIGHT")) {
            throw new RuntimeException("Noć se može obraditi samo dok je faza NIGHT.");
        }

        List<GameAction> actions = gameActionRepository.findByLobbyAndPhaseAndRoundNumber(
                lobby,
                "NIGHT",
                gameState.getRoundNumber()
        );

        GameAction killAction = actions.stream()
                .filter(action -> action.getActionType().equals("KILL"))
                .findFirst()
                .orElse(null);

        GameAction saveAction = actions.stream()
                .filter(action -> action.getActionType().equals("SAVE"))
                .findFirst()
                .orElse(null);

        List<Long> silencedPlayerIds = actions.stream()
                .filter(action -> action.getActionType().equals("SILENCE"))
                .map(action -> action.getTargetPlayer().getId())
                .toList();

        Long killedPlayerId = null;

        if (killAction != null) {

            Player killedPlayer = killAction.getTargetPlayer();

            boolean wasSaved = saveAction != null
                    && saveAction.getTargetPlayer().getId().equals(killedPlayer.getId());

            if (!wasSaved) {
                killedPlayer.setIsAlive(false);
                playerRepository.save(killedPlayer);

                killedPlayerId = killedPlayer.getId();
            }
        }

        String winner = checkWinCondition(lobby);

        if (winner != null) {
            finishGame(lobby, gameState);

            return new ResolveNightResponse(
                    winner,
                    gameState.getPhase(),
                    gameState.getRoundNumber(),
                    killedPlayerId,
                    silencedPlayerIds
            );
        }

        gameState.setPhase("DAY");
        gameState.setUpdateAt(OffsetDateTime.now());
        gameStateRepository.save(gameState);

        return new ResolveNightResponse(
                "Noć je obrađena.",
                gameState.getPhase(),
                gameState.getRoundNumber(),
                killedPlayerId,
                silencedPlayerIds
        );
    }
    private String checkWinCondition(Lobby lobby) {
        List<Player> alivePlayers = playerRepository.findByLobbyAndIsAliveTrue(lobby);

        long mafiaCount = alivePlayers.stream()
                .filter(player ->
                        player.getRole().getName().equalsIgnoreCase("Mafia") ||
                                player.getRole().getName().equalsIgnoreCase("Dama")
                )
                .count();

        long othersCount = alivePlayers.size() - mafiaCount;

        if (mafiaCount == 0) {
            return "CITIZENS_WIN";
        }

        if (mafiaCount >= othersCount) {
            return "MAFIA_WIN";
        }

        return null;
    }

    private void finishGame(Lobby lobby, GameState gameState) {
        lobby.setStatus("FINISHED");
        lobby.setEndedAt(OffsetDateTime.now());

        gameState.setPhase("FINISHED");
        gameState.setUpdateAt(OffsetDateTime.now());

        lobbyRepository.save(lobby);
        gameStateRepository.save(gameState);
    }

    private boolean hasAliveMafia(Lobby lobby) {

        return playerRepository.findByLobbyAndIsAliveTrue(lobby)
                .stream()
                .anyMatch(player ->
                        player.getRole().getName().equalsIgnoreCase("Mafia")
                );
    }
}