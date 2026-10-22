package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.dto.Response.ActionResponse;
import com.niko.ai_mafija_backend.dto.Response.CurrentPlayerGameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.PublicGameStateResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertBetween;
import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Noć: redoslijed poteza, rokovi, glumljeni potezi, dogovor mafije i Dama */
class NightRulesTests extends GameTestSupport {

    @Test
    void firstNightStartsWithLongerIntroThenMafiaTurn() {
        Lobby lobby = lobby();
        player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        GameState state = state(lobby, "NIGHT", 1);

        actionService.startNight(lobby, state, 1);
        state = reload(lobby);
        assertNull(state.getCurrentTurnRole());
        assertBetween(state.getPhaseEndsAt(), 25, 31);

        expire(lobby);
        state = reload(lobby);
        assertEquals("MAFIA", state.getCurrentTurnRole());
        // 6 s naracije + 20 s poteza
        assertBetween(state.getPhaseEndsAt(), 24, 27);
    }

    @Test
    void rolesThatAreNotInTheGameAreSkipped() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", citizen);
        expire(lobby);

        // nema Dame ni Policajca: poslije mafije odmah Doktor
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
    }

    @Test
    void turnLastsUntilDeadlineEvenWhenEveryoneActed() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        actionService.startNight(lobby, state(lobby, "NIGHT", 1), 1);
        expire(lobby);
        GameState before = reload(lobby);
        assertEquals("MAFIA", before.getCurrentTurnRole());

        ActionResponse response = act(mafia, "KILL", citizen);

        // trajanje ne smije otkriti je li uloga odigrala (ili je mrtva)
        assertFalse(response.isNightResolved());
        GameState after = reload(lobby);
        assertEquals("MAFIA", after.getCurrentTurnRole());
        assertEquals(before.getPhaseEndsAt().toEpochSecond(), after.getPhaseEndsAt().toEpochSecond());
    }

    @Test
    void deadRoleTurnLastsAsLongAsALivingOne() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Doktor", false);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", citizen);
        expire(lobby);

        GameState state = reload(lobby);
        assertEquals("Doktor", state.getCurrentTurnRole());
        assertBetween(state.getPhaseEndsAt(), 24, 27);

        expire(lobby);
        assertEquals("DAY", reload(lobby).getPhase());
    }

    @Test
    void mafiaMustAgreeOnTarget() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        Player second = player(lobby, "Mafia", true);
        Player doctor = player(lobby, "Doktor", true);
        List<Player> c = List.of(player(lobby, "Građanin", true), player(lobby, "Građanin", true),
                player(lobby, "Građanin", true));
        nightTurn(lobby, "MAFIA");

        act(first, "KILL", c.get(0));
        act(second, "KILL", c.get(1));
        assertEquals("MAFIA", reload(lobby).getCurrentTurnRole());

        // suigrači vide odabir jedan drugoga i mogu promijeniti metu
        CurrentPlayerGameStateResponse view = gameStateService.getCurrentPlayerState(reload(first), reload(lobby));
        assertEquals(2, view.teamTargets().size());
        assertEquals(1, view.teammates().size());
        assertFalse(view.availableActions().isEmpty());

        act(second, "KILL", c.get(0));
        expire(lobby);
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());

        act(doctor, "SAVE", doctor);
        expire(lobby);
        assertFalse(reload(c.get(0)).getIsAlive());
        assertEquals("NIGHT", reload(c.get(0)).getEliminatedHow());
    }

    @Test
    void mafiaTieOnTimeoutKillsNobody() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        Player second = player(lobby, "Mafia", true);
        List<Player> c = List.of(player(lobby, "Građanin", true), player(lobby, "Građanin", true),
                player(lobby, "Građanin", true), player(lobby, "Građanin", true));
        nightTurn(lobby, "MAFIA");

        act(first, "KILL", c.get(0));
        act(second, "KILL", c.get(1));
        expire(lobby);

        assertEquals("DAY", reload(lobby).getPhase());
        assertTrue(c.stream().allMatch(player -> reload(player).getIsAlive()));
    }

    @Test
    void mafiaCannotTargetOwnTeam() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        Player dama = player(lobby, "Dama", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        assertCode(ErrorCode.INVALID_TARGET, () -> act(mafia, "KILL", dama));
    }

    @Test
    void damaKillsInMafiaTurnWhenMafiaIsDeadAndHerOwnTurnIsFaked() {
        Lobby lobby = lobby();
        player(lobby, "Mafia", false);
        Player dama = player(lobby, "Dama", true);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        CurrentPlayerGameStateResponse view = gameStateService.getCurrentPlayerState(dama, reload(lobby));
        assertTrue(view.canAct());
        assertEquals("KILL", view.availableActions().getFirst().type());

        act(dama, "KILL", citizen);
        expire(lobby);
        GameState state = reload(lobby);
        assertEquals("DAMA", state.getCurrentTurnRole());
        assertBetween(state.getPhaseEndsAt(), 24, 27);

        expire(lobby);
        assertFalse(reload(citizen).getIsAlive());
    }

    @Test
    void damaCannotSilenceMafiaTarget() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        Player dama = player(lobby, "Dama", true);
        Player target = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", target);
        expire(lobby);
        assertEquals("DAMA", reload(lobby).getCurrentTurnRole());
        assertCode(ErrorCode.INVALID_TARGET, () -> act(dama, "SILENCE", target));
    }

    @Test
    void nightResolvesIntoDiscussionWithMorningReport() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        Player dama = player(lobby, "Dama", true);
        Player victim = player(lobby, "Građanin", true);
        Player silenced = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", victim);
        expire(lobby);
        ActionResponse last = act(dama, "SILENCE", silenced);

        assertFalse(last.isNightResolved());
        assertEquals("NIGHT", reload(lobby).getPhase());
        expire(lobby);
        GameState state = reload(lobby);
        assertEquals("DAY", state.getPhase());
        assertBetween(state.getPhaseEndsAt(), 145, 151);

        PublicGameStateResponse publicState = gameStateService.getPublicGameState(lobby.getCode());
        assertEquals(List.of(victim.getId()), publicState.nightKilledPlayerIds());
        assertEquals(List.of(silenced.getId()), publicState.silencedPlayerIds());
        assertNull(publicState.voting());
    }

    @Test
    void publicStateTellsWhichRoleIsAwake() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Dama", true);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        GameState state = state(lobby, "NIGHT", 1);

        actionService.startNight(lobby, state, 1);
        assertNull(gameStateService.getPublicGameState(lobby.getCode()).currentTurnRole());

        expire(lobby);
        assertEquals("MAFIA", gameStateService.getPublicGameState(lobby.getCode()).currentTurnRole());

        act(mafia, "KILL", citizen);
        assertEquals("MAFIA", gameStateService.getPublicGameState(lobby.getCode()).currentTurnRole());
        expire(lobby);
        assertEquals("DAMA", gameStateService.getPublicGameState(lobby.getCode()).currentTurnRole());
    }

    @Test
    void wrongTurnIsRejected() {
        Lobby lobby = lobby();
        player(lobby, "Mafia", true);
        Player doctor = player(lobby, "Doktor", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        assertCode(ErrorCode.NOT_YOUR_TURN, () -> act(doctor, "SAVE", doctor));
    }

    private void nightTurn(Lobby lobby, String turn) {
        GameState state = state(lobby, "NIGHT", 1);
        state.setCurrentTurnRole(turn);
        gameStateRepository.save(state);
    }

    private ActionResponse act(Player player, String type, Player target) {
        ActionRequest request = new ActionRequest();
        request.setPlayerId(player.getId());
        request.setPlayerToken(player.getPlayerToken());
        request.setActionType(type);
        request.setTargetPlayerId(target.getId());
        return actionService.submitAction(request);
    }
}
