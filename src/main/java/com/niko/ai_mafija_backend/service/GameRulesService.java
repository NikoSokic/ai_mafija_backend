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
     * Odabiri onih koji ubijaju u potezu MAFIA: id igrača → id mete (samo žive mete).
     * Prazno kad nitko nije odabrao.
     */
    public Map<Long, Long> mafiaChoices(Lobby lobby, GameState gameState) {
        Set<Long> actorIds = turnActors(lobby, TURN_MAFIA).stream().map(Player::getId).collect(Collectors.toSet());
        return nightActions(lobby, gameState.getRoundNumber()).stream()
                .filter(action -> KILL.equals(action.getActionType()))
                .filter(action -> actorIds.contains(action.getPlayer().getId()))
                .filter(action -> isAlive(action.getTargetPlayer()))
                .collect(Collectors.toMap(action -> action.getPlayer().getId(),
                        action -> action.getTargetPlayer().getId(), (first, second) -> second));
    }

    /** Zajednička meta kad su svi živi koji ubijaju odabrali istu; inače null */
    public Long mafiaAgreedTarget(Lobby lobby, GameState gameState) {
        int actorCount = turnActors(lobby, TURN_MAFIA).size();
        Map<Long, Long> choices = mafiaChoices(lobby, gameState);
        Set<Long> targets = Set.copyOf(choices.values());
        return actorCount > 0 && choices.size() == actorCount && targets.size() == 1
                ? targets.iterator().next()
                : null;
    }

    /**
     * Meta mafije po većini odabira, za noć koju je domaćin završio prije kraja poteza MAFIA
     * (inače vrijedi GameState.nightMafiaTargetId). Kod izjednačenja nitko ne strada (null).
     */
    public Long mafiaKillTarget(Lobby lobby, Integer roundNumber) {
        Map<Long, Long> counts = nightActions(lobby, roundNumber).stream()
                .filter(action -> KILL.equals(action.getActionType()))
                .filter(action -> isAlive(action.getPlayer()))
                .collect(Collectors.groupingBy(action -> action.getTargetPlayer().getId(), Collectors.counting()));
        return uniqueLeader(counts);
    }

    /** Kandidati drugog kruga mafije (mete iz prvog kruga); prazno kad drugi krug ne traje */
    public List<Long> mafiaRunoffCandidates(GameState gameState) {
        return isMafiaRunoff(gameState) ? parseIds(gameState.getMafiaRunoffPlayerIds()) : List.of();
    }

    /**
     * Koga igrač smije odabrati za akciju. Sebe smiju Doktor i Dama (utišavanje); mafija ne cilja svoj tim,
     * a u drugom krugu bira samo između kandidata iz prvog kruga.
     */
    public List<Long> allowedTargets(Player actor, GameState gameState, String actionType) {
        boolean selfAllowed = SAVE.equals(actionType) || SILENCE.equals(actionType);
        boolean runoff = KILL.equals(actionType) && isMafiaRunoff(gameState);
        List<Long> candidates = runoff ? mafiaRunoffCandidates(gameState) : List.of();
        return alivePlayers(actor.getLobby()).stream()
                .filter(target -> selfAllowed || !target.getId().equals(actor.getId()))
                .filter(target -> !KILL.equals(actionType) || !isMafiaTeam(target))
                .filter(target -> !runoff || candidates.contains(target.getId()))
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
