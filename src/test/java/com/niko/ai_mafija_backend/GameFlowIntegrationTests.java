package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Response.PublicGameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.ResolveVoteResponse;
import com.niko.ai_mafija_backend.dto.Response.VoteResponse;
import com.niko.ai_mafija_backend.exception.ApiException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dan i glasanje (docs/pravila-igre.md u frontend repou) */
class GameFlowIntegrationTests extends GameTestSupport {

    // ---------- rasprava ----------

    @Test
    void discussionTimeoutStartsVotingForEveryone() {
        Lobby lobby = lobby();
        host(lobby, "Mafia");
        citizens(lobby, 3);
        state(lobby, "DAY", 1);

        expire(lobby);

        GameState state = reload(lobby);
        assertEquals("VOTING", state.getPhase());
        assertBetween(state.getPhaseEndsAt(), 55, 61);
    }

    @Test
    void cannotVoteDuringDiscussion() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        List<Player> citizens = citizens(lobby, 3);
        state(lobby, "DAY", 1);

        assertCode(ErrorCode.WRONG_PHASE, () -> vote(host, citizens.getFirst()));
    }

    @Test
    void onlyHostCanExtendDiscussionOrStartVoting() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        List<Player> citizens = citizens(lobby, 3);
        GameState state = state(lobby, "DAY", 1);
        OffsetDateTime endsAt = state.getPhaseEndsAt();

        assertCode(ErrorCode.NOT_HOST, () -> voteService.extendDiscussionAsHost(lobby.getCode(), auth(citizens.getFirst())));
        voteService.extendDiscussionAsHost(lobby.getCode(), auth(host));
        assertEquals(30, reload(lobby).getPhaseEndsAt().toEpochSecond() - endsAt.toEpochSecond());

        assertCode(ErrorCode.NOT_HOST, () -> voteService.startVotingAsHost(lobby.getCode(), auth(citizens.getFirst())));
        voteService.startVotingAsHost(lobby.getCode(), auth(host));
        assertEquals("VOTING", reload(lobby).getPhase());
    }

    // ---------- tko ispada ----------

    @Test
    void playerWithMostVotesIsEliminatedWhenAbstainersAreFewer() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 5);
        state(lobby, "VOTING", 1);

        vote(c.get(0), c.get(1));
        vote(c.get(2), c.get(1));
        vote(mafia, c.get(1));
        vote(c.get(3), c.get(4));
        // c1 i c4 ne glasaju: računaju se kao suzdržani (2 < 3)
        expire(lobby);

        Player eliminated = reload(c.get(1));
        assertFalse(eliminated.getIsAlive());
        assertEquals("VOTE", eliminated.getEliminatedHow());
        assertEquals(1, eliminated.getEliminatedRound());
        GameState state = reload(lobby);
        assertEquals("NIGHT", state.getPhase());
        assertEquals(2, state.getRoundNumber());
    }

    @Test
    void nobodyLeavesWhenAbstainersOutnumberTheLeader() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 5);
        state(lobby, "VOTING", 1);

        vote(c.get(0), c.get(1));
        vote(c.get(2), c.get(1));
        vote(mafia, null);
        vote(c.get(3), null);
        vote(c.get(4), null);
        VoteResponse last = vote(c.get(1), null);

        assertTrue(last.isVotingResolved());
        assertEquals("NO_ELIMINATION", last.getResolveResult().getResult());
        assertEquals(4, last.getResolveResult().getAbstainCount());
        assertTrue(reload(c.get(1)).getIsAlive());
        assertEquals("NIGHT", reload(lobby).getPhase());
    }

    @Test
    void nobodyLeavesWhenAbstainersEqualTheLeader() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 5);
        state(lobby, "VOTING", 1);

        vote(c.get(0), c.get(1));
        vote(c.get(2), c.get(1));
        vote(mafia, c.get(3));
        vote(c.get(4), c.get(2));
        vote(c.get(1), null);
        ResolveVoteResponse result = vote(c.get(3), null).getResolveResult();

        assertEquals("NO_ELIMINATION", result.getResult());
        assertTrue(reload(c.get(1)).getIsAlive());
    }

    @Test
    void cannotVoteForYourself() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        citizens(lobby, 3);
        state(lobby, "VOTING", 1);

        assertCode(ErrorCode.CANNOT_VOTE_SELF, () -> vote(mafia, mafia));
    }

    @Test
    void voteIsFinal() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 3);
        state(lobby, "VOTING", 1);

        vote(mafia, c.getFirst());
        assertCode(ErrorCode.ALREADY_VOTED, () -> vote(mafia, c.get(1)));
    }

    @Test
    void silencedPlayerCannotVoteAndIsNotWaitedFor() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        Player dama = player(lobby, "Dama", true);
        List<Player> c = citizens(lobby, 4);
        silence(lobby, dama, c.getFirst(), 1);
        state(lobby, "VOTING", 1);

        assertCode(ErrorCode.SILENCED, () -> vote(c.getFirst(), mafia));

        vote(mafia, c.get(1));
        vote(dama, c.get(1));
        vote(c.get(1), mafia);
        vote(c.get(2), mafia);
        VoteResponse last = vote(c.get(3), c.get(1));

        // utišani nije glasao, a glasanje je ipak obrađeno; c1 ima 3 glasa, suzdržanih nema
        assertTrue(last.isVotingResolved());
        assertEquals("ELIMINATED", last.getResolveResult().getResult());
        assertEquals(c.get(1).getId(), last.getResolveResult().getEliminatedPlayerId());
    }

    // ---------- izjednačenje ----------

    @Test
    void tieStartsTieBreakWithoutAbstainAndTiedPlayersDoNotVote() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 5);
        state(lobby, "VOTING", 1);

        vote(c.get(0), c.get(1));
        vote(c.get(2), c.get(1));
        vote(c.get(1), c.get(3));
        vote(c.get(4), c.get(3));
        vote(mafia, c.get(0));
        ResolveVoteResponse tie = vote(c.get(3), null).getResolveResult();

        assertEquals("TIE", tie.getResult());
        assertEquals(List.of(c.get(1).getId(), c.get(3).getId()), tie.getTiedPlayerIds());
        GameState state = reload(lobby);
        assertEquals("TIE_BREAK", state.getPhase());
        assertBetween(state.getPhaseEndsAt(), 55, 61);

        assertCode(ErrorCode.TIED_PLAYER_CANNOT_VOTE, () -> vote(c.get(1), c.get(3)));
        assertCode(ErrorCode.ABSTAIN_NOT_ALLOWED, () -> vote(c.get(0), null));
        assertCode(ErrorCode.INVALID_TARGET, () -> vote(c.get(0), c.get(2)));

        vote(c.get(0), c.get(1));
        vote(c.get(2), c.get(1));
        vote(c.get(4), c.get(3));
        ResolveVoteResponse result = vote(mafia, c.get(1)).getResolveResult();

        assertEquals("ELIMINATED", result.getResult());
        assertEquals(c.get(1).getId(), result.getEliminatedPlayerId());
        assertNull(result.getAbstainCount());
    }

    @Test
    void secondTieMeansNobodyLeaves() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 4);
        GameState state = state(lobby, "TIE_BREAK", 1);
        state.setTieBreakPlayerIds(c.get(0).getId() + "," + c.get(1).getId());
        gameStateRepository.save(state);

        vote(c.get(2), c.get(0));
        vote(c.get(3), c.get(1));
        // mafija ne glasa: u ponovnom glasanju nema suzdržanih, rok istekne
        expire(lobby);

        state = reload(lobby);
        assertEquals("NIGHT", state.getPhase());
        assertEquals(2, state.getRoundNumber());
        assertTrue(reload(c.get(0)).getIsAlive());
        assertTrue(reload(c.get(1)).getIsAlive());
        assertTrue(reload(mafia).getIsAlive());
    }

    @Test
    void allPlayersTiedAdvancesToNightWithoutDeadlock() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 2);
        state(lobby, "VOTING", 1);

        vote(mafia, c.get(0));
        vote(c.get(0), c.get(1));
        ResolveVoteResponse result = vote(c.get(1), mafia).getResolveResult();

        assertEquals("NO_ELIMINATION", result.getResult());
        assertEquals("NIGHT", reload(lobby).getPhase());
    }

    @Test
    void hostCanEndVotingEvenWithoutVotes() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        citizens(lobby, 3);
        state(lobby, "VOTING", 1);

        ResolveVoteResponse result = voteService.resolveVoteAsHost(lobby.getCode(), auth(host));

        assertEquals("NO_ELIMINATION", result.getResult());
        assertEquals("NIGHT", reload(lobby).getPhase());
    }

    @Test
    void eliminatingLastMafiaEndsTheGame() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        List<Player> c = citizens(lobby, 3);
        state(lobby, "VOTING", 1);

        vote(c.get(0), mafia);
        vote(c.get(1), mafia);
        vote(c.get(2), mafia);
        ResolveVoteResponse result = vote(mafia, c.get(0)).getResolveResult();

        assertEquals("CITIZENS_WIN", result.getResult());
        assertEquals("FINISHED", reload(lobby).getPhase());
    }

    // ---------- javno stanje ----------

    @Test
    void publicStateShowsLiveVotesAndSilencedPlayers() {
        Lobby lobby = lobby();
        Player mafia = host(lobby, "Mafia");
        Player dama = player(lobby, "Dama", true);
        List<Player> c = citizens(lobby, 4);
        silence(lobby, dama, c.getFirst(), 1);
        state(lobby, "VOTING", 1);

        vote(mafia, c.get(1));
        vote(c.get(2), null);

        PublicGameStateResponse state = gameStateService.getPublicGameState(lobby.getCode());
        assertTrue(state.votingOpen());
        assertEquals(List.of(c.getFirst().getId()), state.silencedPlayerIds());
        assertEquals(5, state.voting().eligibleVoterIds().size());
        assertEquals(2, state.voting().votes().size());
        assertEquals(c.get(1).getId(), state.voting().votes().getFirst().targetPlayerId());
        assertNull(state.voting().votes().get(1).targetPlayerId());
        // uloge su tajne dok igra traje
        assertTrue(state.players().stream().allMatch(player -> player.getRoleName() == null));
        assertTrue(state.players().stream().anyMatch(player -> player.getHost()));
    }

    // ---------- pomoćno ----------

    private List<Player> citizens(Lobby lobby, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> player(lobby, "Građanin", true))
                .toList();
    }

    static void assertCode(ErrorCode code, Executable executable) {
        ApiException exception = assertThrows(ApiException.class, executable);
        assertEquals(code, exception.getCode(), exception.getMessage());
    }

    static void assertBetween(OffsetDateTime time, long minSeconds, long maxSeconds) {
        long seconds = time.toEpochSecond() - OffsetDateTime.now().toEpochSecond();
        assertTrue(seconds >= minSeconds && seconds <= maxSeconds, "rok je za " + seconds + " s");
    }
}
