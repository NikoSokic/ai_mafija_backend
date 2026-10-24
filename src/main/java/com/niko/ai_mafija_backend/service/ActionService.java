package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Response.ActionResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerReadyResponse;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Noć: uvodna pauza, potezi uloga redom (MAFIA → DAMA → Doktor → Policajac → Novinar),
 * svaki s rokom, i obrada noći na kraju.
 *
 * Svaki potez traje isto (TURN_NARRATION + NIGHT_TURN) i ne završava ranije kad svi odigraju;
 * potez mrtve uloge se "glumi" jednako dugo, da nitko ne zna da je uloga mrtva.
 * Mafijaši smiju mijenjati metu do isteka roka; tada vrijedi većina.
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
        validateTarget(player, target, actionType, allowedTargets);
        if (secondTarget != null) {
            validateTarget(player, secondTarget, actionType, allowedTargets);
        }

        // Mafijaši smiju promijeniti metu do isteka poteza; ostale uloge igraju jednom
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

        // potez traje do isteka roka i kad su svi odigrali, pa se noć ovdje nikad ne obrađuje
        ResolveNightResponse resolveResult = null;
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

    /**
     * "Spreman sam" u uvodu prve noći. Kad su spremni svi živi igrači, odmah počinje prvi potez.
     * Ponovni poziv nije greška: ništa se ne mijenja i ne šalje se novi događaj.
     */
    @Transactional
    public PlayerReadyResponse markReady(String lobbyCode, PlayerAuthRequest request) {
        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
        Player player = playerAuthService.authenticate(request.playerId(), request.playerToken());
        if (!player.getLobby().getId().equals(lobby.getId())) {
            throw new ForbiddenException(ErrorCode.NOT_IN_LOBBY, "Igrač ne pripada ovom lobbyju.");
        }
        GameState gameState = lockGameState(lobby);
        assertFirstNightIntro(gameState, "Spreman se može javiti samo u uvodu prve noći.");
        if (!isAlive(player)) {
            throw new ForbiddenException(ErrorCode.DEAD_PLAYER, "Mrtav igrač ne može javiti da je spreman.");
        }

        if (Boolean.TRUE.equals(player.getIsReady())) {
            return readyCounts(lobby, player);
        }
        player.setIsReady(true);
        playerRepository.save(player);

        PlayerReadyResponse response = readyCounts(lobby, player);
        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "PLAYER_READY", "Igrač je spreman.", response);
        if (response.readyCount() >= response.readyTotal()) {
            advanceTurn(lobby, gameState);
        } else {
            webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
        }
        return response;
    }

    /** Domaćin preskače uvod prve noći: prvi potez počinje odmah, i kad nisu svi spremni. */
    @Transactional
    public void beginNightAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        GameState gameState = lockGameState(lobby);
        assertFirstNightIntro(gameState, "Noć se može pokrenuti samo u uvodu prve noći.");
        advanceTurn(lobby, gameState);
    }

    private void assertFirstNightIntro(GameState gameState, String message) {
        if (FINISHED.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.GAME_FINISHED, "Igra je završena.");
        }
        if (!isFirstNightIntro(gameState)) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, message);
        }
    }

    private PlayerReadyResponse readyCounts(Lobby lobby, Player player) {
        List<Player> alive = playerRepository.findByLobbyAndIsAliveTrue(lobby);
        int readyCount = (int) alive.stream().filter(other -> Boolean.TRUE.equals(other.getIsReady())).count();
        return new PlayerReadyResponse(player.getId(), readyCount, alive.size());
    }

    /**
     * Početak noći: uvodna pauza, pa prvi potez. Na prvoj noći igrači gledaju svoju ulogu; uvod završava kad su
     * svi spremni, kad domaćin preskoči ili najkasnije nakon FIRST_NIGHT_INTRO_MAX.
     */
    public void startNight(Lobby lobby, GameState gameState, int roundNumber) {
        OffsetDateTime now = OffsetDateTime.now();
        gameState.setPhase(NIGHT);
        gameState.setRoundNumber(roundNumber);
        gameState.setCurrentTurnRole(null);
        gameState.setTieBreakPlayerIds(null);
        gameState.setNightKilledPlayerIds(null);
        gameState.setNightSilencedPlayerIds(null);
        gameState.setPhaseEndsAt(now.plus(roundNumber == 1 ? FIRST_NIGHT_INTRO_MAX : NIGHT_INTRO));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    /** Istekao je uvod ili rok poteza: tko nije odigrao, gubi potez. */
    public void handleNightTimeout(Lobby lobby, GameState gameState) {
        advanceTurn(lobby, gameState);
    }

    /** Sljedeći potez noći, ili obrada noći kad potezi ponestanu */
    private ResolveNightResponse advanceTurn(Lobby lobby, GameState gameState) {
        String nextTurn = nextTurn(lobby, gameState.getCurrentTurnRole());
        if (nextTurn == null) {
            return resolveNight(lobby, gameState);
        }

        OffsetDateTime now = OffsetDateTime.now();
        gameState.setCurrentTurnRole(nextTurn);
        gameState.setPhaseEndsAt(now.plus(TURN_NARRATION).plus(NIGHT_TURN));
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
        gameState.setNightSilencedPlayerIds(GameRulesService.joinIds(silencedIds));
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

    private void validateTarget(Player actor, Player target, String actionType, List<Long> allowedTargets) {
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
