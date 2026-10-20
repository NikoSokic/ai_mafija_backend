package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.VoteRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Response.VoteResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ForbiddenException;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.Vote;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.VoteRepository;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import lombok.RequiredArgsConstructor;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.niko.ai_mafija_backend.dto.Response.ResolveVoteResponse;
import com.niko.ai_mafija_backend.model.Lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final VoteRepository voteRepository;
    private final LobbyRepository lobbyRepository;
    private final PlayerAuthService playerAuthService;
    private final WebSocketService webSocketService;
    private final GameActionRepository gameActionRepository;
    private final ActionService actionService;
    private final GameOutcomeService gameOutcomeService;

    @Transactional
    public VoteResponse submitVote(VoteRequest request) {

        Player voter = playerAuthService.authenticate(
                request.getVoterPlayerId(),
                request.getPlayerToken()
        );

        if (request.getTargetPlayerId() == null) {
            throw new BadRequestException("Meta glasa je obavezna.");
        }

        Player target = playerRepository.findById(request.getTargetPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Igrač za kojeg se glasa ne postoji."));

        if (!voter.getLobby().getId().equals(target.getLobby().getId())) {
            throw new ForbiddenException("Igrač i meta nisu u istom lobbyju.");
        }

        if (!voter.getIsAlive()) {
            throw new ForbiddenException("Mrtav igrač ne može glasati.");
        }

        if (!target.getIsAlive()) {
            throw new ConflictException("Ne možeš glasati za mrtvog igrača.");
        }

        GameState gameState = gameStateRepository.findByLobby(voter.getLobby())
                .orElseThrow(() -> new ConflictException("Game state ne postoji."));

        if (gameState.getPhase().equals("FINISHED")) {
            throw new ConflictException("Igra je završena.");
        }

        if (!gameState.getPhase().equals("DAY") && !gameState.getPhase().equals("TIE_BREAK")) {
            throw new ConflictException("Glasanje je moguće samo tijekom DAY ili TIE_BREAK faze.");
        }

        if (isSilencedDuringCurrentVoting(voter, gameState)) {
            throw new ForbiddenException("Utišani igrač ne može glasati u ovoj rundi.");
        }

        if (gameState.getPhase().equals("TIE_BREAK")) {
            List<Long> tiedPlayerIds = parseTiedPlayerIds(gameState.getTieBreakPlayerIds());

            if (tiedPlayerIds.contains(voter.getId())) {
                throw new ForbiddenException("Izjednačeni igrači ne glasaju u tie-breaku.");
            }

            if (!tiedPlayerIds.contains(target.getId())) {
                throw new ForbiddenException("U tie-break glasanju možeš glasati samo za izjednačene igrače.");
            }
        }

        boolean alreadyVoted = voteRepository.existsByLobbyAndVoterPlayerAndRoundNumberAndPhase(
                voter.getLobby(),
                voter,
                gameState.getRoundNumber(),
                gameState.getPhase()
        );

        if (alreadyVoted) {
            throw new ConflictException("Već si glasao u ovoj rundi.");
        }

        Vote vote = Vote.builder()
                .lobby(voter.getLobby())
                .voterPlayer(voter)
                .targetPlayer(target)
                .roundNumber(gameState.getRoundNumber())
                .phase(gameState.getPhase())
                .createdAt(OffsetDateTime.now())
                .build();

        Vote savedVote = voteRepository.save(vote);
        webSocketService.publishGameStateChangedAfterCommit(voter.getLobby().getCode());

        if (haveAllRequiredPlayersVoted(voter.getLobby(), gameState)) {
            ResolveVoteResponse resolveResult = resolveVote(voter.getLobby().getCode());

            return new VoteResponse(
                    savedVote.getId(),
                    savedVote.getVoterPlayer().getId(),
                    savedVote.getTargetPlayer().getId(),
                    "Glas je spremljen. Svi potrebni igrači su glasali, glasanje je automatski obrađeno.",
                    true,
                    resolveResult
            );
        }

        return new VoteResponse(
                savedVote.getId(),
                savedVote.getVoterPlayer().getId(),
                savedVote.getTargetPlayer().getId(),
                "Glas je spremljen.",
                false,
                null
        );
    }

    @Transactional
    public ResolveVoteResponse resolveVote(String lobbyCode) {

        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException("Lobby ne postoji."));

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElseThrow(() -> new ConflictException("Game state ne postoji."));

        if (!gameState.getPhase().equals("DAY") && !gameState.getPhase().equals("TIE_BREAK")) {
            throw new ConflictException("Glasanje se može obraditi samo tijekom DAY ili TIE_BREAK faze.");
        }

        if (gameState.getPhase().equals("TIE_BREAK")
                && getEligibleVoters(lobby, gameState).isEmpty()) {
            return advanceToNextNightWithoutElimination(lobby, gameState, parseTiedPlayerIds(gameState.getTieBreakPlayerIds()));
        }

        List<Vote> votes = voteRepository.findByLobbyAndRoundNumberAndPhase(
                lobby,
                gameState.getRoundNumber(),
                gameState.getPhase()
        );

        if (votes.isEmpty()) {
            throw new ConflictException("Nema glasova za obradu.");
        }

        Map<Player, Long> voteCounts = votes.stream()
                .collect(Collectors.groupingBy(
                        Vote::getTargetPlayer,
                        Collectors.counting()
                ));

        Long maxVotes = voteCounts.values()
                .stream()
                .max(Long::compareTo)
                .orElse(0L);

        List<Player> topPlayers = voteCounts.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(maxVotes))
                .map(Map.Entry::getKey)
                .toList();

        if (topPlayers.size() > 1) {
            List<Long> tiedPlayerIds = topPlayers.stream()
                    .map(Player::getId)
                    .toList();

            if (gameState.getPhase().equals("DAY")) {
                gameState.setPhase("TIE_BREAK");
                gameState.setTieBreakPlayerIds(convertTiedPlayerIdsToString(tiedPlayerIds));
                gameState.setUpdateAt(OffsetDateTime.now());
                gameStateRepository.save(gameState);
                webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

                if (getEligibleVoters(lobby, gameState).isEmpty()) {
                    return advanceToNextNightWithoutElimination(lobby, gameState, tiedPlayerIds);
                }

                return new ResolveVoteResponse(
                        "TIE",
                        "Tie-break glasanje počinje između izjednačenih igrača.",
                        gameState.getRoundNumber(),
                        null,
                        tiedPlayerIds
                );
            }

            return advanceToNextNightWithoutElimination(lobby, gameState, tiedPlayerIds);
        }

        Player eliminatedPlayer = topPlayers.get(0);
        eliminatedPlayer.setIsAlive(false);
        playerRepository.save(eliminatedPlayer);
        gameState.setTieBreakPlayerIds(null);

        String winner = gameOutcomeService.finishGameIfWinnerExists(lobby, gameState);

        if (winner != null) {
            return new ResolveVoteResponse(
                    winner,
                    "Igra je završena.",
                    gameState.getRoundNumber(),
                    eliminatedPlayer.getId(),
                    new ArrayList<>()
            );
        }

        gameState.setPhase("NIGHT");
        gameState.setCurrentTurnRole("MAFIA");
        gameState.setRoundNumber(gameState.getRoundNumber() + 1);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameStateRepository.save(gameState);
        actionService.initializeNightTurn(lobby, gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

        return new ResolveVoteResponse(
                "ELIMINATED",
                "Igrač je izbačen.",
                gameState.getRoundNumber() - 1,
                eliminatedPlayer.getId(),
                new ArrayList<>()
        );
    }

    @Transactional
    public ResolveVoteResponse resolveVoteAsHost(String lobbyCode, HostActionRequest request) {
        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new ResourceNotFoundException("Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        return resolveVote(lobbyCode);
    }

    private String convertTiedPlayerIdsToString(List<Long> tiedPlayerIds) {
        return tiedPlayerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Long> parseTiedPlayerIds(String tiedPlayerIdsText) {
        if (tiedPlayerIdsText == null || tiedPlayerIdsText.isBlank()) {
            return new ArrayList<>();
        }

        return List.of(tiedPlayerIdsText.split(","))
                .stream()
                .map(Long::parseLong)
                .toList();
    }

    private boolean haveAllRequiredPlayersVoted(Lobby lobby, GameState gameState) {
        List<Long> requiredVoterIds = getEligibleVoters(lobby, gameState).stream()
                .map(Player::getId)
                .toList();

        List<Vote> votes = voteRepository.findByLobbyAndRoundNumberAndPhase(
                lobby,
                gameState.getRoundNumber(),
                gameState.getPhase()
        );

        List<Long> votedPlayerIds = votes.stream()
                .map(vote -> vote.getVoterPlayer().getId())
                .distinct()
                .toList();

        return votedPlayerIds.containsAll(requiredVoterIds);
    }

    private ResolveVoteResponse advanceToNextNightWithoutElimination(
            Lobby lobby,
            GameState gameState,
            List<Long> tiedPlayerIds) {
        Integer resolvedRound = gameState.getRoundNumber();
        gameState.setPhase("NIGHT");
        gameState.setCurrentTurnRole("MAFIA");
        gameState.setRoundNumber(resolvedRound + 1);
        gameState.setTieBreakPlayerIds(null);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameStateRepository.save(gameState);
        actionService.initializeNightTurn(lobby, gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

        return new ResolveVoteResponse(
                "NO_ELIMINATION",
                "Izjednačenje nije razriješeno. Nitko ne ispada.",
                resolvedRound,
                null,
                tiedPlayerIds
        );
    }

    private List<Player> getEligibleVoters(Lobby lobby, GameState gameState) {
        List<Long> tiedPlayerIds = parseTiedPlayerIds(gameState.getTieBreakPlayerIds());
        return playerRepository.findByLobbyAndIsAliveTrue(lobby).stream()
                .filter(player -> !isSilencedDuringCurrentVoting(player, gameState))
                .filter(player -> !"TIE_BREAK".equals(gameState.getPhase()) || !tiedPlayerIds.contains(player.getId()))
                .toList();
    }

    private boolean isSilencedDuringCurrentVoting(Player player, GameState gameState) {
        return gameActionRepository.findByLobbyAndPhaseAndRoundNumber(
                        player.getLobby(), "NIGHT", gameState.getRoundNumber())
                .stream()
                .anyMatch(action -> "SILENCE".equals(action.getActionType())
                        && action.getTargetPlayer().getId().equals(player.getId()));
    }
}
