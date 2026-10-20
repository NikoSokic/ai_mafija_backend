package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Response.ActionResponse;
import com.niko.ai_mafija_backend.dto.Response.ResolveNightResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ForbiddenException;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.niko.ai_mafija_backend.model.Lobby;


import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ActionService {

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final GameActionRepository gameActionRepository;
    private final PlayerAuthService playerAuthService;
    private final WebSocketService webSocketService;
    private final GameOutcomeService gameOutcomeService;

    @Transactional
    public ActionResponse submitAction(ActionRequest request) {

        Player player = playerAuthService.authenticate(
                request.getPlayerId(),
                request.getPlayerToken()
        );

        if (request.getTargetPlayerId() == null) {
            throw new BadRequestException("Meta je obavezna za akciju.");
        }

        Player targetPlayer = playerRepository.findById(request.getTargetPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Meta ne postoji."));

        Player secondTargetPlayer = null;

        if (request.getSecondTargetPlayerId() != null) {
            secondTargetPlayer = playerRepository.findById(request.getSecondTargetPlayerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Druga meta ne postoji."));
        }

        if (!player.getLobby().getId().equals(targetPlayer.getLobby().getId())) {
            throw new ForbiddenException("Igrač i meta nisu u istom lobbyju.");
        }

        if (secondTargetPlayer != null &&
                !player.getLobby().getId().equals(secondTargetPlayer.getLobby().getId())) {
            throw new ForbiddenException("Druga meta nije u istom lobbyju.");
        }

        if (!player.getIsAlive()) {
            throw new ForbiddenException("Mrtav igrač ne može izvršiti akciju.");
        }

        if (secondTargetPlayer != null && !secondTargetPlayer.getIsAlive()) {
            throw new ConflictException("Ne možeš ciljati mrtvog igrača kao drugu metu.");
        }

        if (!targetPlayer.getIsAlive()) {
            throw new ConflictException("Ne možeš ciljati mrtvog igrača.");
        }

        GameState gameState = gameStateRepository.findByLobby(player.getLobby())
                .orElseThrow(() -> new ConflictException("Game state ne postoji."));

        if (gameState.getPhase().equals("FINISHED")) {
            throw new ConflictException("Igra je završena.");
        }

        if (!gameState.getPhase().equals("NIGHT")) {
            throw new ConflictException("Akcije se mogu slati samo tijekom noći.");
        }

        if (!isPlayersRoleCurrentTurn(player, gameState)) {
            throw new ForbiddenException("Nije red na tvoju ulogu.");
        }

        validateRoleAction(player, request.getActionType());

        validateJournalistAction(
                player,
                targetPlayer,
                secondTargetPlayer
        );

        validateDamaDoesNotSilenceMafiaTarget(player, targetPlayer, gameState);

        boolean actionAlreadyExists = gameActionRepository.existsByPlayerAndPhaseAndRoundNumber(
                player,
                gameState.getPhase(),
                gameState.getRoundNumber()
        );

        if (actionAlreadyExists) {
            throw new ConflictException("Već si odigrao potez u ovoj fazi.");
        }

        GameAction action = GameAction.builder()
                .player(player)
                .targetPlayer(targetPlayer)
                .secondTargetPlayer(secondTargetPlayer)
                .lobby(player.getLobby())
                .actionType(request.getActionType())
                .phase(gameState.getPhase())
                .roundNumber(gameState.getRoundNumber())
                .createdAt(OffsetDateTime.now())
                .build();

        GameAction savedAction = gameActionRepository.save(action);

        String investigationResult = null;
        String journalistResult = null;

        if (request.getActionType().equals("COMPARE")) {
            journalistResult = getJournalistResult(targetPlayer, secondTargetPlayer);
        }

        if (request.getActionType().equals("INVESTIGATE")) {
            investigationResult = getInvestigationResult(targetPlayer);
        }

        ResolveNightResponse resolveResult = advanceNightTurnIfCurrentRoleFinished(
                player.getLobby(),
                gameState
        );
        webSocketService.publishGameStateChangedAfterCommit(player.getLobby().getCode());

        return new ActionResponse(
                savedAction.getId(),
                savedAction.getActionType(),
                savedAction.getPlayer().getId(),
                savedAction.getTargetPlayer().getId(),
                resolveResult == null
                        ? "Akcija je spremljena."
                        : "Akcija je spremljena. Noć je automatski obrađena.",
                investigationResult,
                journalistResult,
                resolveResult != null,
                resolveResult
        );
    }

    private void validateRoleAction(Player player, String actionType) {

        if (player.getRole() == null) {
            throw new ConflictException("Igrač nema dodijeljenu ulogu.");
        }

        String roleName = player.getRole().getName();

        switch (roleName) {
            case "Mafia" -> {
                if (!actionType.equals("KILL")) {
                    throw new ForbiddenException("Mafia može koristiti samo KILL akciju.");
                }
            }

            case "Doktor" -> {
                if (!actionType.equals("SAVE")) {
                    throw new ForbiddenException("Doktor može koristiti samo SAVE akciju.");
                }
            }

            case "Policajac" -> {
                if (!actionType.equals("INVESTIGATE")) {
                    throw new ForbiddenException("Policajac može koristiti samo INVESTIGATE akciju.");
                }
            }

            case "Dama" -> {

                boolean aliveMafiaExists = hasAliveMafia(player.getLobby());

                if (aliveMafiaExists) {

                        if (!actionType.equals("SILENCE")) {
                        throw new ForbiddenException(
                                "Dama može koristiti samo SILENCE dok je Mafia živa."
                        );
                    }

                } else {

                    if (!actionType.equals("KILL")) {
                        throw new ForbiddenException(
                                "Dama preuzima KILL kada nema žive Mafije."
                        );
                    }
                }
            }

            case "Građanin" -> {
                throw new ForbiddenException("Građanin nema noćnu akciju.");
            }

            case "Novinar" -> {
                if (!actionType.equals("COMPARE")) {
                    throw new ForbiddenException("Novinar može koristiti samo COMPARE akciju.");
                }
            }

            default -> throw new ForbiddenException("Nepoznata uloga: " + roleName);
        }
    }

    @Transactional
    public ResolveNightResponse resolveNight(String lobbyCode) {

        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException("Lobby ne postoji."));

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElseThrow(() -> new ConflictException("Game state ne postoji."));

        if (gameState.getPhase().equals("FINISHED")) {
            throw new ConflictException("Igra je završena.");
        }

        if (!gameState.getPhase().equals("NIGHT")) {
            throw new ConflictException("Noć se može obraditi samo dok je faza NIGHT.");
        }

        List<GameAction> actions = gameActionRepository.findByLobbyAndPhaseAndRoundNumber(
                lobby,
                "NIGHT",
                gameState.getRoundNumber()
        );

        GameAction killAction = actions.stream()
                .filter(action -> action.getActionType().equals("KILL"))
                .min(Comparator.comparing(GameAction::getId))
                .orElse(null);

        List<GameAction> saveActions = actions.stream()
                .filter(action -> action.getActionType().equals("SAVE"))
                .toList();

        List<Long> silencedPlayerIds = actions.stream()
                .filter(action -> action.getActionType().equals("SILENCE"))
                .map(action -> action.getTargetPlayer().getId())
                .toList();

        List<Long> killedPlayerIds = new ArrayList<>();

        Map<Long, Long> saveCountsByPlayerId = saveActions.stream()
                .collect(Collectors.groupingBy(
                        saveAction -> saveAction.getTargetPlayer().getId(),
                        Collectors.counting()
                ));

        List<Long> overdosePlayerIds = saveCountsByPlayerId.entrySet()
                .stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(Map.Entry::getKey)
                .toList();

        for (Long overdosePlayerId : overdosePlayerIds) {
            Player overdosePlayer = playerRepository.findById(overdosePlayerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Igrač ne postoji."));

            if (overdosePlayer.getIsAlive()) {
                overdosePlayer.setIsAlive(false);
                playerRepository.save(overdosePlayer);
                killedPlayerIds.add(overdosePlayer.getId());
            }
        }

        if (killAction != null) {

            Player mafiaTarget = killAction.getTargetPlayer();

            long saveCountForMafiaTarget = saveCountsByPlayerId.getOrDefault(
                    mafiaTarget.getId(),
                    0L
            );

            boolean wasSavedByExactlyOneDoctor = saveCountForMafiaTarget == 1;
            boolean diedFromOverdose = overdosePlayerIds.contains(mafiaTarget.getId());

            if (!wasSavedByExactlyOneDoctor || diedFromOverdose) {
                if (mafiaTarget.getIsAlive()) {
                    mafiaTarget.setIsAlive(false);
                    playerRepository.save(mafiaTarget);
                    killedPlayerIds.add(mafiaTarget.getId());
                }
            }
        }



        String winner = gameOutcomeService.finishGameIfWinnerExists(lobby, gameState);

        if (winner != null) {
            return new ResolveNightResponse(
                    winner,
                    gameState.getPhase(),
                    gameState.getRoundNumber(),
                    killedPlayerIds,
                    silencedPlayerIds
            );
        }

        gameState.setPhase("DAY");
        gameState.setCurrentTurnRole(null);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameStateRepository.save(gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

        return new ResolveNightResponse(
                "Noć je obrađena.",
                gameState.getPhase(),
                gameState.getRoundNumber(),
                killedPlayerIds,
                silencedPlayerIds
        );
    }

    @Transactional
    public ResolveNightResponse resolveNightAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException("Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        return resolveNight(lobbyCode);
    }

    private boolean hasAliveMafia(Lobby lobby) {

        return playerRepository.findByLobbyAndIsAliveTrue(lobby)
                .stream()
                .anyMatch(player ->
                        player.getRole().getName().equalsIgnoreCase("Mafia")
                );
    }

    private String getInvestigationResult(Player targetPlayer) {

        String roleName = targetPlayer.getRole().getName();

        if (roleName.equalsIgnoreCase("Mafia")) {
            return "MAFIA";
        }

        if (roleName.equalsIgnoreCase("Dama")) {

            boolean aliveMafiaExists = hasAliveMafia(targetPlayer.getLobby());

            if (!aliveMafiaExists) {
                return "MAFIA";
            }

            return "NOT_MAFIA";
        }

        return "NOT_MAFIA";
    }

    private boolean isPlayersRoleCurrentTurn(Player player, GameState gameState) {

        String currentTurnRole = gameState.getCurrentTurnRole();

        if (currentTurnRole == null) {
            return false;
        }

        String playerRole = player.getRole().getName();

        if (currentTurnRole.equalsIgnoreCase("MAFIA")) {
            return playerRole.equalsIgnoreCase("Mafia");
        }

        if (currentTurnRole.equalsIgnoreCase("DAMA")) {
            return playerRole.equalsIgnoreCase("Dama");
        }

        return playerRole.equalsIgnoreCase(currentTurnRole);
    }

    private ResolveNightResponse advanceNightTurnIfCurrentRoleFinished(Lobby lobby, GameState gameState) {
        if (!haveAllPlayersFromCurrentTurnRoleActed(lobby, gameState)) {
            return null;
        }

        String nextRole = getNextAvailableNightRole(lobby, gameState.getCurrentTurnRole());

        if (nextRole == null) {
            gameState.setCurrentTurnRole(null);
            gameState.setUpdateAt(OffsetDateTime.now());
            gameStateRepository.save(gameState);

            return resolveNight(lobby.getCode());
        }

        gameState.setCurrentTurnRole(nextRole);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameStateRepository.save(gameState);

        return null;
    }

    public void initializeNightTurn(Lobby lobby, GameState gameState) {
        if (!"NIGHT".equals(gameState.getPhase())) {
            return;
        }

        String firstRole = getFirstAvailableNightRole(lobby);
        if (firstRole == null) {
            gameState.setCurrentTurnRole(null);
            gameState.setUpdateAt(OffsetDateTime.now());
            gameStateRepository.save(gameState);
            resolveNight(lobby.getCode());
            return;
        }

        gameState.setCurrentTurnRole(firstRole);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameStateRepository.save(gameState);
    }

    private boolean haveAllPlayersFromCurrentTurnRoleActed(Lobby lobby, GameState gameState) {
        List<Player> alivePlayers = playerRepository.findByLobbyAndIsAliveTrue(lobby);

        List<Long> requiredPlayerIds = alivePlayers.stream()
                .filter(player -> player.getRole() != null)
                .filter(player -> isRolePartOfCurrentTurn(player, gameState.getCurrentTurnRole()))
                .map(Player::getId)
                .toList();

        List<GameAction> actions = gameActionRepository.findByLobbyAndPhaseAndRoundNumber(
                lobby,
                gameState.getPhase(),
                gameState.getRoundNumber()
        );

        List<Long> actedPlayerIds = actions.stream()
                .map(action -> action.getPlayer().getId())
                .distinct()
                .toList();

        return actedPlayerIds.containsAll(requiredPlayerIds);
    }

    private boolean isRolePartOfCurrentTurn(Player player, String currentTurnRole) {

        if (currentTurnRole == null) {
            return false;
        }

        String roleName = player.getRole().getName();

        if (currentTurnRole.equalsIgnoreCase("MAFIA")) {
            return roleName.equalsIgnoreCase("Mafia");
        }

        if (currentTurnRole.equalsIgnoreCase("DAMA")) {
            return roleName.equalsIgnoreCase("Dama");
        }

        return roleName.equalsIgnoreCase(currentTurnRole);
    }

    private boolean hasAlivePlayerForTurnRole(Lobby lobby, String turnRole) {

        List<Player> alivePlayers = playerRepository.findByLobbyAndIsAliveTrue(lobby);

        return alivePlayers.stream()
                .anyMatch(player -> isRolePartOfTurnRole(player, turnRole));
    }

    private boolean isRolePartOfTurnRole(Player player, String turnRole) {

        String roleName = player.getRole().getName();

        if (turnRole.equals("MAFIA")) {
            return roleName.equalsIgnoreCase("Mafia");
        }

        return roleName.equalsIgnoreCase(turnRole);
    }

    private String getNextAvailableNightRole(Lobby lobby, String currentTurnRole)   {

        List<String> nightOrder = List.of(
                "MAFIA",
                "DAMA",
                "Doktor",
                "Policajac",
                "Novinar"
        );

        int currentIndex = nightOrder.indexOf(currentTurnRole);

        for (int i = currentIndex + 1; i < nightOrder.size(); i++) {
            String nextRole = nightOrder.get(i);

            if (hasAlivePlayerForTurnRole(lobby, nextRole)) {
                return nextRole;
            }
        }

        return null;
    }

    private String getFirstAvailableNightRole(Lobby lobby) {
        for (String role : List.of("MAFIA", "DAMA", "Doktor", "Policajac", "Novinar")) {
            if (hasAlivePlayerForTurnRole(lobby, role)) {
                return role;
            }
        }
        return null;
    }

    private void validateDamaDoesNotSilenceMafiaTarget(Player player, Player targetPlayer, GameState gameState) {
        if (!player.getRole().getName().equalsIgnoreCase("Dama")) {
            return;
        }

        if (!gameState.getCurrentTurnRole().equals("DAMA")) {
            return;
        }

        if (!hasAliveMafia(player.getLobby())) {
            return;
        }

        List<GameAction> actions = gameActionRepository.findByLobbyAndPhaseAndRoundNumber(
                player.getLobby(),
                gameState.getPhase(),
                gameState.getRoundNumber()
        );

        boolean targetIsMafiaKillTarget = actions.stream()
                .anyMatch(action ->
                        action.getActionType().equals("KILL")
                                && action.getTargetPlayer().getId().equals(targetPlayer.getId())
                );

        if (targetIsMafiaKillTarget) {
            throw new ConflictException("Dama ne može utišati igrača kojeg je Mafia odabrala za KILL.");
        }
    }

    private void validateJournalistAction(Player player, Player targetPlayer, Player secondTargetPlayer) {
        if (!player.getRole().getName().equalsIgnoreCase("Novinar")) {
            return;
        }

        if (secondTargetPlayer == null) {
            throw new BadRequestException("Novinar mora odabrati dva igrača.");
        }

        if (targetPlayer.getId().equals(secondTargetPlayer.getId())) {
            throw new BadRequestException("Novinar mora odabrati dva različita igrača.");
        }
    }

    private String getJournalistResult(Player firstPlayer, Player secondPlayer) {

        boolean firstMafiaTeam = isMafiaTeam(firstPlayer);
        boolean secondMafiaTeam = isMafiaTeam(secondPlayer);

        if (firstMafiaTeam == secondMafiaTeam) {
            return "ISTI_TIM";
        }

        return "RAZLICIT_TIM";
    }

    private boolean isMafiaTeam(Player player) {

        String roleName = player.getRole().getName();

        return roleName.equalsIgnoreCase("Mafia")
                || roleName.equalsIgnoreCase("Dama");
    }


}
