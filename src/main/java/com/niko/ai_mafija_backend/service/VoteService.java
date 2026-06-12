package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.VoteRequest;
import com.niko.ai_mafija_backend.dto.Response.VoteResponse;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.Vote;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
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

    public VoteResponse submitVote(VoteRequest request) {

        Player voter = playerAuthService.authenticate(
                request.getVoterPlayerId(),
                request.getPlayerToken()
        );

        if (request.getTargetPlayerId() == null) {
            throw new RuntimeException("Meta glasa je obavezna.");
        }

        Player target = playerRepository.findById(request.getTargetPlayerId())
                .orElseThrow(() -> new RuntimeException("Igrač za kojeg se glasa ne postoji."));

        if (!voter.getLobby().getId().equals(target.getLobby().getId())) {
            throw new RuntimeException("Igrač i meta nisu u istom lobbyju.");
        }

        if (!voter.getIsAlive()) {
            throw new RuntimeException("Mrtav igrač ne može glasati.");
        }

        if (!target.getIsAlive()) {
            throw new RuntimeException("Ne možeš glasati za mrtvog igrača.");
        }

        GameState gameState = gameStateRepository.findByLobby(voter.getLobby())
                .orElseThrow(() -> new RuntimeException("Game state ne postoji."));

        if (gameState.getPhase().equals("FINISHED")) {
            throw new RuntimeException("Igra je završena.");
        }

        if (!gameState.getPhase().equals("DAY") && !gameState.getPhase().equals("TIE_BREAK")) {
            throw new RuntimeException("Glasanje je moguće samo tijekom DAY ili TIE_BREAK faze.");
        }

        if (gameState.getPhase().equals("TIE_BREAK")) {
            List<Long> tiedPlayerIds = parseTiedPlayerIds(gameState.getTieBreakPlayerIds());

            if (tiedPlayerIds.contains(voter.getId())) {
                throw new RuntimeException("Izjednačeni igrači ne glasaju u tie-breaku.");
            }

            if (!tiedPlayerIds.contains(target.getId())) {
                throw new RuntimeException("U tie-break glasanju možeš glasati samo za izjednačene igrače.");
            }
        }

        boolean alreadyVoted = voteRepository.existsByLobbyAndVoterPlayerAndRoundNumberAndPhase(
                voter.getLobby(),
                voter,
                gameState.getRoundNumber(),
                gameState.getPhase()
        );

        if (alreadyVoted) {
            throw new RuntimeException("Već si glasao u ovoj rundi.");
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

    public ResolveVoteResponse resolveVote(String lobbyCode) {

        Lobby lobby = lobbyRepository.findByCode(lobbyCode)
                .orElseThrow(() -> new RuntimeException("Lobby ne postoji."));

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElseThrow(() -> new RuntimeException("Game state ne postoji."));

        if (!gameState.getPhase().equals("DAY") && !gameState.getPhase().equals("TIE_BREAK")) {
            throw new RuntimeException("Glasanje se može obraditi samo tijekom DAY ili TIE_BREAK faze.");
        }

        List<Vote> votes = voteRepository.findByLobbyAndRoundNumberAndPhase(
                lobby,
                gameState.getRoundNumber(),
                gameState.getPhase()
        );

        if (votes.isEmpty()) {
            throw new RuntimeException("Nema glasova za obradu.");
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

                return new ResolveVoteResponse(
                        "TIE",
                        "Tie-break glasanje počinje između izjednačenih igrača.",
                        gameState.getRoundNumber(),
                        null,
                        tiedPlayerIds
                );
            }

            gameState.setPhase("NIGHT");
            gameState.setCurrentTurnRole("MAFIA");
            gameState.setRoundNumber(gameState.getRoundNumber() + 1);
            gameState.setTieBreakPlayerIds(null);
            gameState.setUpdateAt(OffsetDateTime.now());
            gameStateRepository.save(gameState);

            return new ResolveVoteResponse(
                    "NO_ELIMINATION",
                    "Opet je izjednačeno. Nitko ne ispada.",
                    gameState.getRoundNumber() - 1,
                    null,
                    tiedPlayerIds
            );
        }

        Player eliminatedPlayer = topPlayers.get(0);
        eliminatedPlayer.setIsAlive(false);
        playerRepository.save(eliminatedPlayer);
        gameState.setTieBreakPlayerIds(null);

        String winner = checkWinCondition(lobby);

        if (winner != null) {
            finishGame(lobby, gameState);

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

        return new ResolveVoteResponse(
                "ELIMINATED",
                "Igrač je izbačen.",
                gameState.getRoundNumber() - 1,
                eliminatedPlayer.getId(),
                new ArrayList<>()
        );
    }

    private String checkWinCondition(Lobby lobby) {
        List<Player> alivePlayers = playerRepository.findByLobbyAndIsAliveTrue(lobby);

        long mafiaCount = alivePlayers.stream()
                .filter(player ->
                        player.getRole().getName().equalsIgnoreCase("MAFIA") ||
                                player.getRole().getName().equalsIgnoreCase("DAMA")
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

        List<Player> alivePlayers = playerRepository.findByLobbyAndIsAliveTrue(lobby);

        List<Long> requiredVoterIds;

        if (gameState.getPhase().equals("TIE_BREAK")) {
            List<Long> tiedPlayerIds = parseTiedPlayerIds(gameState.getTieBreakPlayerIds());

            requiredVoterIds = alivePlayers.stream()
                    .map(Player::getId)
                    .filter(playerId -> !tiedPlayerIds.contains(playerId))
                    .toList();
        } else {
            requiredVoterIds = alivePlayers.stream()
                    .map(Player::getId)
                    .toList();
        }

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
}