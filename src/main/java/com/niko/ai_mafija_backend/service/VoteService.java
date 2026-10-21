package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.VoteRequest;
import com.niko.ai_mafija_backend.dto.Response.ResolveVoteResponse;
import com.niko.ai_mafija_backend.dto.Response.VoteCountResponse;
import com.niko.ai_mafija_backend.dto.Response.VoteResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
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
import com.niko.ai_mafija_backend.repository.VoteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Dan: rasprava (DAY, bez glasanja) → glasanje (VOTING, 60 s) → po potrebi ponovno glasanje
 * između izjednačenih (TIE_BREAK, 60 s) → noć ili kraj igre.
 *
 * Pravilo izbacivanja: ispada igrač s najviše glasova, ali samo ako ima STROGO više glasova od
 * suzdržanih (u što se broje i oni koji nisu glasali). U ponovnom glasanju nema suzdržanih.
 */
@Service
@RequiredArgsConstructor
public class VoteService {

    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final VoteRepository voteRepository;
    private final LobbyRepository lobbyRepository;
    private final PlayerAuthService playerAuthService;
    private final WebSocketService webSocketService;
    private final ActionService actionService;
    private final GameOutcomeService gameOutcomeService;
    private final GameRulesService rules;

    @Transactional
    public VoteResponse submitVote(VoteRequest request) {

        Player voter = playerAuthService.authenticate(request.getVoterPlayerId(), request.getPlayerToken());
        Lobby lobby = voter.getLobby();
        GameState gameState = lockGameState(lobby);
        String phase = gameState.getPhase();

        if (FINISHED.equals(phase)) {
            throw new ConflictException(ErrorCode.GAME_FINISHED, "Igra je završena.");
        }
        if (!isVotingPhase(phase)) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Glasanje trenutno nije otvoreno.");
        }
        if (!isAlive(voter)) {
            throw new ForbiddenException(ErrorCode.DEAD_PLAYER, "Mrtav igrač ne može glasati.");
        }
        if (rules.isSilenced(voter, gameState)) {
            throw new ForbiddenException(ErrorCode.SILENCED, "Utišani igrač ne može glasati u ovoj rundi.");
        }
        boolean tieBreak = TIE_BREAK.equals(phase);
        List<Long> tiedIds = rules.tiedPlayerIds(gameState);
        if (tieBreak && tiedIds.contains(voter.getId())) {
            throw new ForbiddenException(ErrorCode.TIED_PLAYER_CANNOT_VOTE, "Izjednačeni igrači ne glasaju u tie-breaku.");
        }
        if (voteRepository.existsByLobbyAndVoterPlayerAndRoundNumberAndPhase(
                lobby, voter, gameState.getRoundNumber(), phase)) {
            throw new ConflictException(ErrorCode.ALREADY_VOTED, "Već si glasao u ovoj rundi.");
        }

        Player target = null;
        if (request.getTargetPlayerId() == null) {
            if (tieBreak) {
                throw new BadRequestException(ErrorCode.ABSTAIN_NOT_ALLOWED,
                        "U ponovnom glasanju moraš odabrati jednog od izjednačenih igrača.");
            }
        } else {
            target = playerRepository.findById(request.getTargetPlayerId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND,
                            "Igrač za kojeg se glasa ne postoji."));
            if (!target.getLobby().getId().equals(lobby.getId())) {
                throw new ForbiddenException(ErrorCode.NOT_IN_LOBBY, "Igrač i meta nisu u istom lobbyju.");
            }
            if (target.getId().equals(voter.getId())) {
                throw new BadRequestException(ErrorCode.CANNOT_VOTE_SELF, "Ne možeš glasati za sebe.");
            }
            if (!isAlive(target)) {
                throw new ConflictException(ErrorCode.INVALID_TARGET, "Ne možeš glasati za mrtvog igrača.");
            }
            if (tieBreak && !tiedIds.contains(target.getId())) {
                throw new ForbiddenException(ErrorCode.INVALID_TARGET,
                        "U tie-break glasanju možeš glasati samo za izjednačene igrače.");
            }
        }

        Vote savedVote = voteRepository.save(Vote.builder()
                .lobby(lobby)
                .voterPlayer(voter)
                .targetPlayer(target)
                .roundNumber(gameState.getRoundNumber())
                .phase(phase)
                .createdAt(OffsetDateTime.now())
                .build());

        ResolveVoteResponse resolveResult = resolveIfAllVoted(lobby, gameState);
        if (resolveResult == null) {
            // glasovi su javni: svi odmah vide novi glas
            webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
        }

        return new VoteResponse(
                savedVote.getId(),
                voter.getId(),
                target != null ? target.getId() : null,
                resolveResult == null
                        ? "Glas je spremljen."
                        : "Glas je spremljen. Svi potrebni igrači su glasali, glasanje je obrađeno.",
                resolveResult != null,
                resolveResult
        );
    }

    /** Domaćin pokreće glasanje prije kraja rasprave – svima istovremeno */
    @Transactional
    public void startVotingAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = findLobby(lobbyCode);
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        GameState gameState = lockGameState(lobby);
        assertDiscussion(gameState);
        startVoting(lobby, gameState);
    }

    /** Domaćin produži raspravu svima za 30 s */
    @Transactional
    public void extendDiscussionAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = findLobby(lobbyCode);
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        GameState gameState = lockGameState(lobby);
        assertDiscussion(gameState);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime base = gameState.getPhaseEndsAt() != null && gameState.getPhaseEndsAt().isAfter(now)
                ? gameState.getPhaseEndsAt()
                : now;
        gameState.setPhaseEndsAt(base.plus(DISCUSSION_EXTENSION));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    /** Domaćin završava glasanje prije roka (npr. netko je zatvorio preglednik) */
    @Transactional
    public ResolveVoteResponse resolveVoteAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = findLobby(lobbyCode);
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        GameState gameState = lockGameState(lobby);

        if (FINISHED.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.GAME_FINISHED, "Igra je završena.");
        }
        if (!isVotingPhase(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Glasanje se može obraditi samo dok je glasanje otvoreno.");
        }
        return resolveVote(lobby, gameState);
    }

    /** Kraj rasprave: glasanje počinje svima u isto vrijeme */
    public void startVoting(Lobby lobby, GameState gameState) {
        OffsetDateTime now = OffsetDateTime.now();
        gameState.setPhase(VOTING_PHASE);
        gameState.setTieBreakPlayerIds(null);
        gameState.setPhaseEndsAt(now.plus(VOTING));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);

        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "VOTING_STARTED", "Glasanje je počelo.",
                Map.of("roundNumber", gameState.getRoundNumber(), "phaseEndsAt", gameState.getPhaseEndsAt()));
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

        // npr. svi živi su utišani – nema koga čekati
        if (rules.eligibleVoters(lobby, gameState).isEmpty()) {
            resolveVote(lobby, gameState);
        }
    }

    /** Obradi glasanje ako su glasali svi koji smiju; inače null */
    public ResolveVoteResponse resolveIfAllVoted(Lobby lobby, GameState gameState) {
        if (!isVotingPhase(gameState.getPhase())) {
            return null;
        }
        Set<Long> votedIds = rules.currentVotes(lobby, gameState).stream()
                .map(vote -> vote.getVoterPlayer().getId())
                .collect(Collectors.toSet());
        boolean allVoted = rules.eligibleVoters(lobby, gameState).stream()
                .allMatch(voter -> votedIds.contains(voter.getId()));
        return allVoted ? resolveVote(lobby, gameState) : null;
    }

    /** Prebroji glasove i odluči: izbacivanje, ponovno glasanje ili nitko ne ispada */
    public ResolveVoteResponse resolveVote(Lobby lobby, GameState gameState) {
        boolean tieBreak = TIE_BREAK.equals(gameState.getPhase());
        List<Player> voters = rules.eligibleVoters(lobby, gameState);
        Set<Long> voterIds = voters.stream().map(Player::getId).collect(Collectors.toSet());
        Set<Long> candidateIds = Set.copyOf(tieBreak
                ? rules.tiedPlayerIds(gameState)
                : rules.alivePlayers(lobby).stream().map(Player::getId).toList());

        // Broje se samo glasovi onih koji i dalje smiju glasati, za igrače koji su i dalje u igri
        Map<Long, Long> counts = rules.currentVotes(lobby, gameState).stream()
                .filter(vote -> voterIds.contains(vote.getVoterPlayer().getId()))
                .filter(vote -> vote.getTargetPlayer() != null)
                .filter(vote -> candidateIds.contains(vote.getTargetPlayer().getId()))
                .collect(Collectors.groupingBy(vote -> vote.getTargetPlayer().getId(), Collectors.counting()));
        List<VoteCountResponse> voteCounts = counts.entrySet().stream()
                .map(entry -> new VoteCountResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(VoteCountResponse::votes).reversed()
                        .thenComparing(VoteCountResponse::playerId))
                .toList();

        long top = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Long> leaders = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == top)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (tieBreak) {
            if (top == 0 || leaders.size() > 1) {
                return noElimination(lobby, gameState, leaders, voteCounts, null,
                        "Opet izjednačeno. Danas nitko ne odlazi.");
            }
            return eliminateByVote(lobby, gameState, leaders.getFirst(), voteCounts, null);
        }

        long votesForPlayers = counts.values().stream().mapToLong(Long::longValue).sum();
        int abstainCount = (int) (voters.size() - votesForPlayers);
        if (top == 0 || abstainCount >= top) {
            return noElimination(lobby, gameState, List.of(), voteCounts, abstainCount,
                    "Suzdržanih je bilo barem koliko i glasova za vodećeg. Danas nitko ne odlazi.");
        }
        if (leaders.size() > 1) {
            return startTieBreak(lobby, gameState, leaders, voteCounts, abstainCount);
        }
        return eliminateByVote(lobby, gameState, leaders.getFirst(), voteCounts, abstainCount);
    }

    private ResolveVoteResponse startTieBreak(Lobby lobby, GameState gameState, List<Long> tiedIds,
                                              List<VoteCountResponse> voteCounts, Integer abstainCount) {
        OffsetDateTime now = OffsetDateTime.now();
        gameState.setPhase(TIE_BREAK);
        gameState.setTieBreakPlayerIds(GameRulesService.joinIds(tiedIds));
        gameState.setPhaseEndsAt(now.plus(VOTING));
        gameState.setUpdateAt(now);
        gameStateRepository.save(gameState);

        // svi živi su izjednačeni (ili utišani) – nema tko glasati
        if (rules.eligibleVoters(lobby, gameState).isEmpty()) {
            return noElimination(lobby, gameState, tiedIds, voteCounts, abstainCount,
                    "Izjednačenje se ne može razriješiti. Danas nitko ne odlazi.");
        }

        ResolveVoteResponse response = new ResolveVoteResponse(
                VOTE_TIE,
                "Izjednačeno. Glasa se ponovno samo između izjednačenih.",
                gameState.getRoundNumber(),
                null,
                tiedIds,
                voteCounts,
                abstainCount
        );
        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "VOTE_RESOLVED", response.getMessage(), response);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
        return response;
    }

    private ResolveVoteResponse noElimination(Lobby lobby, GameState gameState, List<Long> tiedIds,
                                              List<VoteCountResponse> voteCounts, Integer abstainCount,
                                              String message) {
        Integer roundNumber = gameState.getRoundNumber();
        ResolveVoteResponse response = new ResolveVoteResponse(
                VOTE_NO_ELIMINATION, message, roundNumber, null, tiedIds, voteCounts, abstainCount);
        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "VOTE_RESOLVED", message, response);
        actionService.startNight(lobby, gameState, roundNumber + 1);
        return response;
    }

    private ResolveVoteResponse eliminateByVote(Lobby lobby, GameState gameState, Long eliminatedId,
                                                List<VoteCountResponse> voteCounts, Integer abstainCount) {
        Integer roundNumber = gameState.getRoundNumber();
        Player eliminated = playerRepository.findById(eliminatedId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND, "Igrač ne postoji."));
        gameOutcomeService.eliminate(eliminated, ELIMINATED_VOTE, roundNumber);
        gameState.setTieBreakPlayerIds(null);

        String winner = gameOutcomeService.determineWinner(lobby);
        ResolveVoteResponse response = new ResolveVoteResponse(
                winner != null ? winner : VOTE_ELIMINATED,
                winner != null ? "Igra je završena." : "Igrač je izbačen.",
                roundNumber,
                eliminatedId,
                List.of(),
                voteCounts,
                abstainCount
        );
        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "VOTE_RESOLVED", response.getMessage(), response);

        if (winner != null) {
            gameOutcomeService.finishGameIfWinnerExists(lobby, gameState);
        } else {
            actionService.startNight(lobby, gameState, roundNumber + 1);
        }
        return response;
    }

    private void assertDiscussion(GameState gameState) {
        if (FINISHED.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.GAME_FINISHED, "Igra je završena.");
        }
        if (!DAY.equals(gameState.getPhase())) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Rasprava trenutno ne traje.");
        }
    }

    private Lobby findLobby(String lobbyCode) {
        return lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
    }

    private GameState lockGameState(Lobby lobby) {
        return gameStateRepository.findByLobbyForUpdate(lobby)
                .orElseThrow(() -> new ConflictException(ErrorCode.GAME_NOT_STARTED, "Game state ne postoji."));
    }
}
