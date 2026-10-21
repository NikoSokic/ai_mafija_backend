package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Response.ActionResponse;
import com.niko.ai_mafija_backend.dto.Response.ResolveNightResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.exception.ForbiddenException;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Noć: uvodna pauza, potezi uloga redom (MAFIA → DAMA → Doktor → Policajac → Novinar),
 * svaki s rokom, i obrada noći na kraju.
 *
 * Potez uloge čiji su igrači mrtvi se "glumi" (čeka nasumičnih 8–15 s), da nitko ne zna da je
 * uloga mrtva. Mafijaši smiju mijenjati metu dok se svi ne slože; ako istekne rok, vrijedi većina.
 */
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
    private final GameRulesService rules;

    @Transactional
    public ActionResponse submitAction(ActionRequest request) {

        if (request.getTargetPlayerId() == null) {
            throw new BadRequestException(ErrorCode.INVALID_TARGET, "Meta je obavezna za akciju.");
        }
        if (request.getActionType() == null) {
            throw new BadRequestException(ErrorCode.INVALID_ACTION, "Vrsta akcije je obavezna.");
        }

        Player player = playerAuthService.authenticate(request.getPlayerId(), request.getPlayerToken());
        Lobby lobby = player.getLobby();
        GameState gameState = lockGameState(lobby);

        if (FINISHED.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.GAME_FINISHED, "Igra je završena.");
        }
        if (!NIGHT.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Akcije se mogu slati samo tijekom noći.");
        }
        if (!isAlive(player)) {
            throw new ForbiddenException(ErrorCode.DEAD_PLAYER, "Mrtav igrač ne može izvršiti akciju.");
        }
        if (!rules.canAct(player, gameState)) {
            throw new ForbiddenException(ErrorCode.NOT_YOUR_TURN, "Nije red na tvoju ulogu.");
        }

        String actionType = actionTypeForTurn(gameState.getCurrentTurnRole());
        if (!actionType.equals(request.getActionType())) {
            throw new ForbiddenException(ErrorCode.INVALID_ACTION,
                    "U ovom potezu možeš koristiti samo akciju " + actionType + ".");
        }

        Player target = findTarget(request.getTargetPlayerId(), lobby);
        Player secondTarget = null;
        if (COMPARE.equals(actionType)) {
            if (request.getSecondTargetPlayerId() == null) {
                throw new BadRequestException(ErrorCode.INVALID_TARGET, "Novinar mora odabrati dva igrača.");
            }
            if (request.getSecondTargetPlayerId().equals(request.getTargetPlayerId())) {
                throw new BadRequestException(ErrorCode.INVALID_TARGET, "Novinar mora odabrati dva različita igrača.");
            }
            secondTarget = findTarget(request.getSecondTargetPlayerId(), lobby);
        }

        List<Long> allowedTargets = rules.allowedTargets(player, gameState, actionType);
        validateTarget(player, target, actionType, allowedTargets, gameState);
        if (secondTarget != null) {
            validateTarget(player, secondTarget, actionType, allowedTargets, gameState);
        }

        // Mafijaši smiju promijeniti metu dok se ne slože; ostale uloge igraju jednom
        GameAction action = gameActionRepository
                .findFirstByPlayerAndPhaseAndRoundNumber(player, NIGHT, gameState.getRoundNumber())
                .orElse(null);
        if (action != null && !KILL.equals(actionType)) {
            throw new ConflictException(ErrorCode.ALREADY_ACTED, "Već si odigrao potez u ovoj fazi.");
        }
        if (action == null) {
            action = GameAction.builder()
                    .player(player)
                    .lobby(lobby)
                    .actionType(actionType)
                    .phase(NIGHT)
                    .roundNumber(gameState.getRoundNumber())
                    .build();
        }
        action.setTargetPlayer(target);
        action.setSecondTargetPlayer(secondTarget);
        action.setCreatedAt(OffsetDateTime.now());
        GameAction savedAction = gameActionRepository.save(action);

        String investigationResult = INVESTIGATE.equals(actionType) ? getInvestigationResult(target) : null;
        String journalistResult = COMPARE.equals(actionType) ? getJournalistResult(target, secondTarget) : null;

        ResolveNightResponse resolveResult = finishTurnIfComplete(lobby, gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

        return new ActionResponse(
                savedAction.getId(),
                savedAction.getActionType(),
                player.getId(),
                target.getId(),
                resolveResult == null
                        ? "Akcija je spremljena."
                        : "Akcija je spremljena. Noć je automatski obrađena.",
                investigationResult,
                journalistResult,
                resolveResult != null,
                resolveResult
        );
    }

    /** Domaćin završava noć odmah; uloge koje još nisu odigrale gube potez. */
    @Transactional
    public ResolveNightResponse resolveNightAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        GameState gameState = lockGameState(lobby);

        if (FINISHED.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.GAME_FINISHED, "Igra je završena.");
        }
        if (!NIGHT.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Noć se može obraditi samo dok je faza NIGHT.");
        }
        return resolveNight(lobby, gameState);
    }

    /** Početak noći: uvodna pauza (na prvoj noći igrači gledaju svoju ulogu), pa prvi potez. */
    public void startNight(Lobby lobby, GameState gameState, int roundNumber) {
        OffsetDateTime now = OffsetDateTime.now();
        gameState.setPhase(NIGHT);
        gameState.setRoundNumber(roundNumber);
        gameState.setCurrentTurnRole(null);
        gameState.setTieBreakPlayerIds(null);
        gameState.setNightKilledPlayerIds(null);
        gameState.setPhaseEndsAt(now.plus(roundNumber == 1 ? FIRST_NIGHT_INTRO : NIGHT_INTRO));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    /** Istekao je uvod ili rok poteza: tko nije odigrao, gubi potez. */
    public void handleNightTimeout(Lobby lobby, GameState gameState) {
        advanceTurn(lobby, gameState);
    }

    /** Kad su svi igrači poteza odigrali (mafija se i složila oko mete), odmah ide sljedeći potez. */
    public ResolveNightResponse finishTurnIfComplete(Lobby lobby, GameState gameState) {
        if (!NIGHT.equals(gameState.getPhase()) || gameState.getCurrentTurnRole() == null) {
            return null;
        }
        if (!isTurnComplete(lobby, gameState)) {
            return null;
        }
        return advanceTurn(lobby, gameState);
    }

    private boolean isTurnComplete(Lobby lobby, GameState gameState) {
        String turn = gameState.getCurrentTurnRole();
        List<Player> actors = rules.turnActors(lobby, turn);
        if (actors.isEmpty()) {
            // glumljeni potez čeka svoj rok
            return false;
        }

        Map<Long, GameAction> actionsByPlayer = rules.nightActions(lobby, gameState.getRoundNumber()).stream()
                .collect(Collectors.toMap(action -> action.getPlayer().getId(), action -> action, (a, b) -> a));
        if (!actors.stream().allMatch(actor -> actionsByPlayer.containsKey(actor.getId()))) {
            return false;
        }
        if (!TURN_MAFIA.equals(turn)) {
            return true;
        }
        long distinctTargets = actors.stream()
                .map(actor -> actionsByPlayer.get(actor.getId()).getTargetPlayer().getId())
                .distinct()
                .count();
        return distinctTargets == 1;
    }

    /** Sljedeći potez noći, ili obrada noći kad potezi ponestanu */
    private ResolveNightResponse advanceTurn(Lobby lobby, GameState gameState) {
        String nextTurn = nextTurn(lobby, gameState.getCurrentTurnRole());
        if (nextTurn == null) {
            return resolveNight(lobby, gameState);
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean fake = rules.turnActors(lobby, nextTurn).isEmpty();
        gameState.setCurrentTurnRole(nextTurn);
        gameState.setPhaseEndsAt(now.plus(fake ? fakeTurnDuration() : NIGHT_TURN));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
        return null;
    }

    private String nextTurn(Lobby lobby, String currentTurn) {
        int start = currentTurn == null ? 0 : NIGHT_ORDER.indexOf(currentTurn) + 1;
        for (int i = start; i < NIGHT_ORDER.size(); i++) {
            String turn = NIGHT_ORDER.get(i);
            if (rules.turnExistsInGame(lobby, turn)) {
                return turn;
            }
        }
        return null;
    }

    private Duration fakeTurnDuration() {
        return Duration.ofSeconds(ThreadLocalRandom.current().nextInt(FAKE_TURN_MIN_SECONDS, FAKE_TURN_MAX_SECONDS + 1));
    }

    /**
     * Obrada noći: meta mafije (većina) umire, osim ako ju je zaštitio točno jedan Doktor.
     * Dva Doktora na istom igraču = predoziranje (umire). Zatim pobjednik ili dan (rasprava).
     */
    public ResolveNightResponse resolveNight(Lobby lobby, GameState gameState) {
        Integer roundNumber = gameState.getRoundNumber();
        List<GameAction> actions = rules.nightActions(lobby, roundNumber);
        Long mafiaTargetId = rules.mafiaKillTarget(lobby, roundNumber);

        Map<Long, Long> saveCounts = actions.stream()
                .filter(action -> SAVE.equals(action.getActionType()))
                .collect(Collectors.groupingBy(action -> action.getTargetPlayer().getId(), Collectors.counting()));
        List<Long> overdoseIds = saveCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .map(Map.Entry::getKey)
                .toList();

        List<Long> killedIds = new ArrayList<>();
        for (Long overdoseId : overdoseIds) {
            killIfAlive(overdoseId, roundNumber, killedIds);
        }
        if (mafiaTargetId != null && saveCounts.getOrDefault(mafiaTargetId, 0L) != 1) {
            killIfAlive(mafiaTargetId, roundNumber, killedIds);
        }

        List<Long> silencedIds = actions.stream()
                .filter(action -> SILENCE.equals(action.getActionType()))
                .map(action -> action.getTargetPlayer().getId())
                .filter(id -> !killedIds.contains(id))
                .distinct()
                .toList();

        gameState.setNightKilledPlayerIds(GameRulesService.joinIds(killedIds));
        gameState.setCurrentTurnRole(null);
        gameState.setPhaseEndsAt(null);

        String winner = gameOutcomeService.determineWinner(lobby);
        ResolveNightResponse response = new ResolveNightResponse(
                winner != null ? winner : "Noć je obrađena.",
                winner != null ? FINISHED : DAY,
                roundNumber,
                killedIds,
                silencedIds
        );
        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "NIGHT_RESOLVED", "Noć je obrađena.", response);

        if (winner != null) {
            gameOutcomeService.finishGameIfWinnerExists(lobby, gameState);
            return response;
        }

        OffsetDateTime now = OffsetDateTime.now();
        gameState.setPhase(DAY);
        gameState.setPhaseEndsAt(now.plus(DISCUSSION));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
        return response;
    }

    private void killIfAlive(Long playerId, Integer roundNumber, List<Long> killedIds) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND, "Igrač ne postoji."));
        if (isAlive(player)) {
            gameOutcomeService.eliminate(player, ELIMINATED_NIGHT, roundNumber);
            killedIds.add(playerId);
        }
    }

    private GameState lockGameState(Lobby lobby) {
        return gameStateRepository.findByLobbyForUpdate(lobby)
                .orElseThrow(() -> new ConflictException(ErrorCode.GAME_NOT_STARTED, "Game state ne postoji."));
    }

    private Player findTarget(Long targetId, Lobby lobby) {
        Player target = playerRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND, "Meta ne postoji."));
        if (!target.getLobby().getId().equals(lobby.getId())) {
            throw new ForbiddenException(ErrorCode.NOT_IN_LOBBY, "Igrač i meta nisu u istom lobbyju.");
        }
        return target;
    }

    private void validateTarget(Player actor, Player target, String actionType, List<Long> allowedTargets,
                                GameState gameState) {
        if (!isAlive(target)) {
            throw new ConflictException(ErrorCode.INVALID_TARGET, "Ne možeš ciljati mrtvog igrača.");
        }
        if (allowedTargets.contains(target.getId())) {
            return;
        }
        String message;
        if (target.getId().equals(actor.getId())) {
            message = "Ne možeš odabrati sebe.";
        } else if (KILL.equals(actionType) && isMafiaTeam(target)) {
            message = "Ne možeš ciljati člana svog tima.";
        } else if (SILENCE.equals(actionType)
                && target.getId().equals(rules.mafiaKillTarget(actor.getLobby(), gameState.getRoundNumber()))) {
            message = "Dama ne može utišati igrača kojeg je Mafia odabrala za KILL.";
        } else {
            message = "Taj igrač nije dopuštena meta.";
        }
        throw new ConflictException(ErrorCode.INVALID_TARGET, message);
    }

    /** Dama izgleda nevino dok je Mafija živa */
    private String getInvestigationResult(Player target) {
        if (hasRole(target, MAFIA)) {
            return "MAFIA";
        }
        if (hasRole(target, DAMA)) {
            return rules.hasAliveMafia(target.getLobby()) ? "NOT_MAFIA" : "MAFIA";
        }
        return "NOT_MAFIA";
    }

    private String getJournalistResult(Player first, Player second) {
        return isMafiaTeam(first) == isMafiaTeam(second) ? "ISTI_TIM" : "RAZLICIT_TIM";
    }
}
