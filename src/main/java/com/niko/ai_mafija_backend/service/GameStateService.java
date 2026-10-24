package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Response.AvailableActionResponse;
import com.niko.ai_mafija_backend.dto.Response.CurrentPlayerGameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.GameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.LastNightResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.dto.Response.PublicGameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.PublicVotingResponse;
import com.niko.ai_mafija_backend.dto.Response.TeamTargetResponse;
import com.niko.ai_mafija_backend.dto.Response.TeammateResponse;
import com.niko.ai_mafija_backend.dto.Response.VoteEntryResponse;
import com.niko.ai_mafija_backend.dto.Response.VotingStateResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.exception.ForbiddenException;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.Vote;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Stanje igre za klijente: javno (svi, i u WebSocket događaju) i privatno (GET game-state).
 * Uloge ostaju tajne do kraja igre; tim mafije vidi suigrače i njihove noćne mete.
 */
@Service
@RequiredArgsConstructor
public class GameStateService {

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final PlayerAuthService playerAuthService;
    private final GameRulesService rules;

    public GameStateResponse getGameState(String lobbyCode, Long playerId, String playerToken) {
        Lobby lobby = findLobby(lobbyCode);
        Player player = playerAuthService.authenticate(playerId, playerToken);
        assertPlayerBelongsToLobby(player, lobby);

        GameState gameState = gameStateRepository.findByLobby(lobby).orElse(null);
        List<Player> players = playerRepository.findByLobby(lobby);
        boolean finished = isGameFinished(lobby, gameState);

        return new GameStateResponse(
                lobby.getCode(),
                lobby.getStatus(),
                gameState != null ? gameState.getPhase() : null,
                gameState != null ? gameState.getRoundNumber() : null,
                toPublicPlayers(players, finished),
                getCurrentPlayerState(player, gameState),
                finished,
                determineWinner(lobby, gameState, players),
                getVotingState(player, gameState),
                publicPhaseEndsAt(gameState),
                OffsetDateTime.now(),
                gameState != null ? rules.silencedPlayerIds(lobby, gameState) : List.of(),
                nightKilledPlayerIds(gameState),
                publicTurn(gameState),
                lastNight(gameState),
                readyPlayerIds(gameState, players)
        );
    }

    public PublicGameStateResponse getPublicGameState(String lobbyCode) {
        Lobby lobby = findLobby(lobbyCode);
        GameState gameState = gameStateRepository.findByLobby(lobby).orElse(null);
        List<Player> players = playerRepository.findByLobby(lobby);
        boolean finished = isGameFinished(lobby, gameState);
        boolean votingOpen = gameState != null && isVotingPhase(gameState.getPhase());

        return new PublicGameStateResponse(
                lobby.getCode(),
                lobby.getStatus(),
                gameState != null ? gameState.getPhase() : null,
                gameState != null ? gameState.getRoundNumber() : null,
                toPublicPlayers(players, finished),
                finished,
                determineWinner(lobby, gameState, players),
                votingOpen,
                gameState != null ? rules.tiedPlayerIds(gameState) : List.of(),
                publicPhaseEndsAt(gameState),
                OffsetDateTime.now(),
                gameState != null ? rules.silencedPlayerIds(lobby, gameState) : List.of(),
                nightKilledPlayerIds(gameState),
                votingOpen ? getPublicVoting(lobby, gameState) : null,
                publicTurn(gameState),
                lastNight(gameState),
                readyPlayerIds(gameState, players)
        );
    }

    public CurrentPlayerGameStateResponse getCurrentPlayerState(Player player, GameState gameState) {
        boolean canAct = gameState != null && rules.canAct(player, gameState);
        boolean hasActed = gameState != null && rules.hasActed(player, gameState);
        boolean mafiaTeam = isMafiaTeam(player);

        // mafijaš smije promijeniti metu do isteka poteza, pa akcija ostaje dostupna i nakon odabira
        boolean canPick = canAct && (!hasActed || TURN_MAFIA.equals(gameState.getCurrentTurnRole()));
        List<AvailableActionResponse> availableActions = List.of();
        if (canPick) {
            String actionType = actionTypeForTurn(gameState.getCurrentTurnRole());
            availableActions = List.of(new AvailableActionResponse(
                    actionType,
                    rules.allowedTargets(player, gameState, actionType),
                    COMPARE.equals(actionType)
            ));
        }

        List<TeammateResponse> teammates = mafiaTeam
                ? rules.mafiaTeam(player.getLobby()).stream()
                .filter(teammate -> !teammate.getId().equals(player.getId()))
                .map(teammate -> new TeammateResponse(
                        teammate.getId(),
                        teammate.getNickname(),
                        teammate.getRole().getName(),
                        isAlive(teammate)))
                .toList()
                : List.of();

        List<TeamTargetResponse> teamTargets = mafiaTeam && gameState != null && NIGHT.equals(gameState.getPhase())
                ? rules.nightActions(player.getLobby(), gameState.getRoundNumber()).stream()
                .filter(action -> KILL.equals(action.getActionType()))
                .filter(action -> isMafiaTeam(action.getPlayer()))
                .sorted(Comparator.comparing(action -> action.getPlayer().getId()))
                .map(action -> new TeamTargetResponse(action.getPlayer().getId(), action.getTargetPlayer().getId()))
                .toList()
                : List.of();

        return new CurrentPlayerGameStateResponse(
                player.getId(),
                player.getNickname(),
                isAlive(player),
                player.getRole() != null ? player.getRole().getName() : null,
                canAct,
                hasActed,
                availableActions,
                canAct ? gameState.getPhaseEndsAt() : null,
                teammates,
                teamTargets,
                player.getEliminatedHow(),
                player.getEliminatedRound()
        );
    }

    private VotingStateResponse getVotingState(Player player, GameState gameState) {
        if (gameState == null || !isVotingPhase(gameState.getPhase())) {
            return new VotingStateResponse(false, false, false, false, List.of(), List.of(), List.of(), List.of());
        }

        Lobby lobby = player.getLobby();
        List<Long> voterIds = rules.eligibleVoters(lobby, gameState).stream().map(Player::getId).toList();
        List<Vote> votes = rules.currentVotes(lobby, gameState);
        boolean hasVoted = votes.stream().anyMatch(vote -> vote.getVoterPlayer().getId().equals(player.getId()));
        boolean canVote = voterIds.contains(player.getId()) && !hasVoted;

        return new VotingStateResponse(
                true,
                canVote,
                hasVoted,
                canVote && VOTING_PHASE.equals(gameState.getPhase()),
                rules.eligibleVoteTargetIds(lobby, gameState, player),
                TIE_BREAK.equals(gameState.getPhase()) ? rules.tiedPlayerIds(gameState) : List.of(),
                voterIds,
                rules.voteEntries(votes)
        );
    }

    private PublicVotingResponse getPublicVoting(Lobby lobby, GameState gameState) {
        List<Long> voterIds = rules.eligibleVoters(lobby, gameState).stream().map(Player::getId).toList();
        List<VoteEntryResponse> votes = rules.voteEntries(rules.currentVotes(lobby, gameState));
        List<Long> tied = TIE_BREAK.equals(gameState.getPhase()) ? rules.tiedPlayerIds(gameState) : List.of();
        return new PublicVotingResponse(voterIds, votes, tied);
    }

    /** Noću se rok ne objavljuje svima (otkrio bi tko je na redu); igrač na redu ga dobije u actionEndsAt */
    private OffsetDateTime publicPhaseEndsAt(GameState gameState) {
        if (gameState == null || NIGHT.equals(gameState.getPhase())) {
            return null;
        }
        return gameState.getPhaseEndsAt();
    }

    /** Tko je na redu smije se znati (narator ga budi naglas); rok ostaje skriven jer bi otkrio glumljeni potez */
    private String publicTurn(GameState gameState) {
        if (gameState == null || !NIGHT.equals(gameState.getPhase())) {
            return null;
        }
        return gameState.getCurrentTurnRole();
    }

    /** Rezultat zadnje noći je javan cijeli dan (rasprava, glasanje, ponovno glasanje); noću i na kraju igre ga nema */
    private LastNightResponse lastNight(GameState gameState) {
        if (gameState == null || !isDaytime(gameState.getPhase())) {
            return null;
        }
        return new LastNightResponse(
                gameState.getRoundNumber(),
                GameRulesService.parseIds(gameState.getNightKilledPlayerIds()),
                GameRulesService.parseIds(gameState.getNightSilencedPlayerIds()));
    }

    /** Tko je spreman vidi se samo u uvodu prve noći; inače null */
    private List<Long> readyPlayerIds(GameState gameState, List<Player> players) {
        if (!isFirstNightIntro(gameState)) {
            return null;
        }
        return players.stream()
                .filter(GameRules::isAlive)
                .filter(player -> Boolean.TRUE.equals(player.getIsReady()))
                .map(Player::getId)
                .sorted()
                .toList();
    }

    private List<Long> nightKilledPlayerIds(GameState gameState) {
        if (gameState == null || !isDaytime(gameState.getPhase())) {
            return List.of();
        }
        return GameRulesService.parseIds(gameState.getNightKilledPlayerIds());
    }

    private boolean isGameFinished(Lobby lobby, GameState gameState) {
        return LOBBY_FINISHED.equals(lobby.getStatus())
                || (gameState != null && FINISHED.equals(gameState.getPhase()));
    }

    private String determineWinner(Lobby lobby, GameState gameState, List<Player> players) {
        if (!isGameFinished(lobby, gameState)) {
            return null;
        }

        String storedWinner = GameOutcomeService.getStoredWinner(gameState);
        if (storedWinner != null) {
            return storedWinner;
        }

        List<Player> alivePlayers = players.stream().filter(GameRules::isAlive).toList();
        long mafiaCount = alivePlayers.stream().filter(GameRules::isMafiaTeam).count();
        long othersCount = alivePlayers.size() - mafiaCount;

        if (mafiaCount == 0) {
            return "CITIZENS_WIN";
        }
        return mafiaCount >= othersCount ? "MAFIA_WIN" : null;
    }

    /** Uloge svih igrača postaju javne tek kad igra završi */
    private List<LobbyPlayerResponse> toPublicPlayers(List<Player> players, boolean revealRoles) {
        return players.stream()
                .sorted(Comparator.comparing(Player::getId))
                .map(player -> LobbyPlayerResponse.from(player, revealRoles))
                .toList();
    }

    private Lobby findLobby(String lobbyCode) {
        return lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
    }

    private void assertPlayerBelongsToLobby(Player player, Lobby lobby) {
        if (!player.getLobby().getId().equals(lobby.getId())) {
            throw new ForbiddenException(ErrorCode.NOT_IN_LOBBY, "Igrač ne pripada ovom lobbyju.");
        }
    }
}
