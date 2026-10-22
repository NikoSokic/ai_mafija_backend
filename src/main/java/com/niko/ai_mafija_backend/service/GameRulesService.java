package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Response.VoteEntryResponse;
import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.Vote;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Pitanja o stanju igre koja trebaju i noć (ActionService), i glasanje (VoteService),
 * i prikaz stanja (GameStateService): tko je na redu, tko smije glasati, tko je utišan…
 * Ovdje se ništa ne sprema.
 */
@Service
@RequiredArgsConstructor
public class GameRulesService {

    private final PlayerRepository playerRepository;
    private final GameActionRepository gameActionRepository;
    private final VoteRepository voteRepository;

    // ---------- igrači ----------

    public List<Player> alivePlayers(Lobby lobby) {
        return playerRepository.findByLobbyAndIsAliveTrue(lobby);
    }

    public boolean hasAliveMafia(Lobby lobby) {
        return alivePlayers(lobby).stream().anyMatch(player -> hasRole(player, MAFIA));
    }

    public List<Player> mafiaTeam(Lobby lobby) {
        return playerRepository.findByLobby(lobby).stream()
                .filter(GameRules::isMafiaTeam)
                .sorted(Comparator.comparing(Player::getId))
                .toList();
    }

    // ---------- dan i glasanje ----------

    /** Izjednačeni igrači u TIE_BREAK. Nakon kraja igre isto polje čuva pobjednika, pa se to preskače. */
    public List<Long> tiedPlayerIds(GameState gameState) {
        return parseIds(gameState.getTieBreakPlayerIds());
    }

    /** Utišani u tekućem danu: mete SILENCE iz noći iste runde koji su još živi */
    public List<Long> silencedPlayerIds(Lobby lobby, GameState gameState) {
        if (!isDaytime(gameState.getPhase())) {
            return List.of();
        }
        Set<Long> aliveIds = alivePlayers(lobby).stream().map(Player::getId).collect(Collectors.toSet());
        return nightActions(lobby, gameState.getRoundNumber()).stream()
                .filter(action -> SILENCE.equals(action.getActionType()))
                .map(action -> action.getTargetPlayer().getId())
                .filter(aliveIds::contains)
                .distinct()
                .toList();
    }

    public boolean isSilenced(Player player, GameState gameState) {
        return silencedPlayerIds(player.getLobby(), gameState).contains(player.getId());
    }

    /** Tko smije glasati: živi i neutišani; u ponovnom glasanju ne glasaju izjednačeni */
    public List<Player> eligibleVoters(Lobby lobby, GameState gameState) {
        if (!isVotingPhase(gameState.getPhase())) {
            return List.of();
        }
        List<Long> silenced = silencedPlayerIds(lobby, gameState);
        List<Long> tied = tiedPlayerIds(gameState);
        boolean tieBreak = TIE_BREAK.equals(gameState.getPhase());
        return alivePlayers(lobby).stream()
                .filter(player -> !silenced.contains(player.getId()))
                .filter(player -> !tieBreak || !tied.contains(player.getId()))
                .sorted(Comparator.comparing(Player::getId))
                .toList();
    }

    /** Za koga glasač smije glasati: živi osim njega; u ponovnom glasanju samo izjednačeni */
    public List<Long> eligibleVoteTargetIds(Lobby lobby, GameState gameState, Player voter) {
        if (!isVotingPhase(gameState.getPhase())) {
            return List.of();
        }
        List<Long> tied = tiedPlayerIds(gameState);
        boolean tieBreak = TIE_BREAK.equals(gameState.getPhase());
        return alivePlayers(lobby).stream()
                .map(Player::getId)
                .filter(id -> !id.equals(voter.getId()))
                .filter(id -> !tieBreak || tied.contains(id))
                .sorted()
                .toList();
    }

    public List<Vote> currentVotes(Lobby lobby, GameState gameState) {
        if (!isVotingPhase(gameState.getPhase())) {
            return List.of();
        }
        return voteRepository.findByLobbyAndRoundNumberAndPhase(
                lobby, gameState.getRoundNumber(), gameState.getPhase());
    }

    public List<VoteEntryResponse> voteEntries(List<Vote> votes) {
        return votes.stream()
                .sorted(Comparator.comparing(Vote::getId))
                .map(vote -> new VoteEntryResponse(
                        vote.getVoterPlayer().getId(),
                        vote.getTargetPlayer() != null ? vote.getTargetPlayer().getId() : null))
                .toList();
    }

    // ---------- noć ----------

    public List<GameAction> nightActions(Lobby lobby, Integer roundNumber) {
        return gameActionRepository.findByLobbyAndPhaseAndRoundNumber(lobby, NIGHT, roundNumber);
    }

    /** Postoji li uloga za ovaj potez u igri (živa ili mrtva). Uloge kojih nema se preskaču. */
    public boolean turnExistsInGame(Lobby lobby, String turn) {
        List<Player> players = playerRepository.findByLobby(lobby);
        return switch (turn) {
            case TURN_MAFIA -> players.stream().anyMatch(GameRules::isMafiaTeam);
            case TURN_DAMA -> players.stream().anyMatch(player -> hasRole(player, DAMA));
            default -> players.stream().anyMatch(player -> hasRole(player, turn));
        };
    }

    /**
     * Tko igra u ovom potezu. Prazno znači da se potez samo glumi (uloga je mrtva).
     * MAFIA: živa Mafija, a kad nje nema – živa Dama. DAMA: Dama utišava samo dok je Mafija živa.
     */
    public List<Player> turnActors(Lobby lobby, String turn) {
        List<Player> alive = alivePlayers(lobby);
        return switch (turn) {
            case TURN_MAFIA -> {
                List<Player> mafia = alive.stream().filter(player -> hasRole(player, MAFIA)).toList();
                yield mafia.isEmpty()
                        ? alive.stream().filter(player -> hasRole(player, DAMA)).toList()
                        : mafia;
            }
            case TURN_DAMA -> alive.stream().anyMatch(player -> hasRole(player, MAFIA))
                    ? alive.stream().filter(player -> hasRole(player, DAMA)).toList()
                    : List.of();
            default -> alive.stream().filter(player -> hasRole(player, turn)).toList();
        };
    }

    public boolean canAct(Player player, GameState gameState) {
        return NIGHT.equals(gameState.getPhase())
                && gameState.getCurrentTurnRole() != null
                && isAlive(player)
                && turnActors(player.getLobby(), gameState.getCurrentTurnRole()).stream()
                .anyMatch(actor -> actor.getId().equals(player.getId()));
    }

    public boolean hasActed(Player player, GameState gameState) {
        return NIGHT.equals(gameState.getPhase())
                && gameActionRepository.existsByPlayerAndPhaseAndRoundNumber(player, NIGHT, gameState.getRoundNumber());
    }

    /**
     * Meta mafije ove noći: ona koju je odabrala većina živih članova koji ubijaju.
     * Kad se slože, to je zajednička meta; kod izjednačenja nitko ne strada (null).
     */
    public Long mafiaKillTarget(Lobby lobby, Integer roundNumber) {
        Map<Long, Long> counts = nightActions(lobby, roundNumber).stream()
                .filter(action -> KILL.equals(action.getActionType()))
                .filter(action -> isAlive(action.getPlayer()))
                .collect(Collectors.groupingBy(action -> action.getTargetPlayer().getId(), Collectors.counting()));
        return uniqueLeader(counts);
    }

    /** Koga igrač smije odabrati za akciju. Sebe samo Doktor; mafija ne cilja svoj tim; Dama ne utišava metu mafije. */
    public List<Long> allowedTargets(Player actor, GameState gameState, String actionType) {
        List<Player> alive = alivePlayers(actor.getLobby());
        Long mafiaTarget = SILENCE.equals(actionType)
                ? mafiaKillTarget(actor.getLobby(), gameState.getRoundNumber())
                : null;
        return alive.stream()
                .filter(target -> SAVE.equals(actionType) || !target.getId().equals(actor.getId()))
                .filter(target -> !KILL.equals(actionType) || !isMafiaTeam(target))
                .filter(target -> mafiaTarget == null || !target.getId().equals(mafiaTarget))
                .map(Player::getId)
                .sorted()
                .toList();
    }

    // ---------- pomoćno ----------

    /** Ključ s najviše glasova; null ako ga nema ili ih je više izjednačenih */
    public static <K> K uniqueLeader(Map<K, Long> counts) {
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        if (max == 0) {
            return null;
        }
        List<K> leaders = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();
        return leaders.size() == 1 ? leaders.getFirst() : null;
    }

    public static List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank() || ids.startsWith(GameOutcomeService.WINNER_PREFIX)) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    public static String joinIds(List<Long> ids) {
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
