package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.dto.Response.ActionResponse;
import com.niko.ai_mafija_backend.dto.Response.CurrentPlayerGameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.GameStateResponse;
import com.niko.ai_mafija_backend.dto.Response.PublicGameStateResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.TurnDuration;
import com.niko.ai_mafija_backend.service.GameRules;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertBetween;
import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Noć: redoslijed poteza, rokovi, kraj nakon odigranog, glumljeni potezi, dogovor i drugi krug mafije, Dama */
class NightRulesTests extends GameTestSupport {

    private static final Duration TAIL_PASSED = GameRules.ACTED_TAIL_MAX.plusMillis(100);

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
        // sigurnosni rok uvoda: 90 s (ranije završava kad su svi spremni ili domaćin preskoči)
        assertBetween(state.getPhaseEndsAt(), 85, 91);
        assertNull(gameStateService.getPublicGameState(lobby.getCode()).phaseEndsAt());

        expire(lobby);
        state = reload(lobby);
        assertEquals("MAFIA", state.getCurrentTurnRole());
        // stvarni rok: 4 s naracije + 13 s odabira
        assertBetween(state.getPhaseEndsAt(), 16, 18);
        // javni rok: najdulji mogući potez mafije (s drugim krugom)
        assertEquals(state.getTurnStartedAt().plusSeconds(4 + 13 + 10),
                gameStateService.getPublicGameState(lobby.getCode()).phaseEndsAt());
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

    // ---------- živa uloga ----------

    @Test
    void livingRoleTurnEndsShortlyAfterActingNotAtDeadline() {
        Lobby lobby = lobby();
        player(lobby, "Mafia", true);
        Player doctor = player(lobby, "Doktor", true);
        player(lobby, "Policajac", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "Doktor");

        ActionResponse response = act(doctor, "SAVE", doctor);
        assertFalse(response.isNightResolved());

        // kraj je 1–4 s nakon odabira, a ne nakon 13 s
        GameState state = reload(lobby);
        long msLeft = Duration.between(OffsetDateTime.now(), state.getPhaseEndsAt()).toMillis();
        assertTrue(msLeft >= 800 && msLeft <= 4000, "potez završava za " + msLeft + " ms");

        elapse(lobby, Duration.ofMillis(300));
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
        elapse(lobby, TAIL_PASSED);
        assertEquals("Policajac", reload(lobby).getCurrentTurnRole());

        // stvarni potez se pamti: od kraja naracije do kraja poteza, s pauzom
        List<TurnDuration> recorded = turnDurationRepository.findAll();
        assertEquals(1, recorded.size());
        assertEquals("Doktor", recorded.getFirst().getTurn());
        assertTrue(recorded.getFirst().getDurationMs() >= 1000 && recorded.getFirst().getDurationMs() <= 4500);
    }

    @Test
    void livingRoleThatDoesNotActEndsAtMaxEvenWhenAway() {
        Lobby lobby = lobby();
        player(lobby, "Mafia", true);
        Player doctor = player(lobby, "Doktor", true);
        player(lobby, "Policajac", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        // Doktor se ne javlja ("Nije tu"), ali je živ: i dalje ima cijeli potez
        doctor.setLastSeenAt(OffsetDateTime.now().minusMinutes(10));
        playerRepository.save(doctor);
        nightTurn(lobby, "Doktor");

        elapse(lobby, Duration.ofMillis(12_500));
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
        elapse(lobby, Duration.ofMillis(600));
        assertEquals("Policajac", reload(lobby).getCurrentTurnRole());

        assertEquals(List.of(13_000L), turnDurationRepository.findAll().stream().map(TurnDuration::getDurationMs).toList());
    }

    // ---------- mrtva uloga (glumljeni potez) ----------

    @Test
    void deadRoleTurnLastsFourToTenSecondsWithoutStoredDurations() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Doktor", false);
        player(lobby, "Policajac", true);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", citizen);
        elapse(lobby, TAIL_PASSED);

        GameState state = reload(lobby);
        assertEquals("Doktor", state.getCurrentTurnRole());
        assertTrue(state.getTurnFake());
        assertBetweenMs(fakeTurnLength(state), 4_000, 10_000);

        // glumljeni potez se ne pamti; potez mafije jest
        expire(lobby);
        assertEquals("Policajac", reload(lobby).getCurrentTurnRole());
        assertEquals(List.of("MAFIA"), turnDurationRepository.findAll().stream().map(TurnDuration::getTurn).toList());

        for (int i = 0; i < 50; i++) {
            assertBetweenMs(turnDurationService.fakeDuration("Doktor"), 4_000, 10_000);
        }
    }

    @Test
    void deadRoleTurnUsesRecentStoredDurations() {
        // starija trajanja (12 s) ne ulaze u zadnjih 100
        for (int i = 0; i < 50; i++) {
            turnDurationService.record("Doktor", Duration.ofSeconds(12));
        }
        for (int i = 0; i < 100; i++) {
            turnDurationService.record("Doktor", Duration.ofSeconds(7));
        }
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Doktor", false);
        Player citizen = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", citizen);
        elapse(lobby, TAIL_PASSED);

        GameState state = reload(lobby);
        assertEquals("Doktor", state.getCurrentTurnRole());
        assertEquals(Duration.ofSeconds(7), fakeTurnLength(state));
    }

    @Test
    void fakeDurationNeedsTenSamplesAndStaysWithinLimits() {
        // 9 trajanja nije dovoljno: zamjensko 4–10 s
        for (int i = 0; i < 9; i++) {
            turnDurationService.record("Novinar", Duration.ofSeconds(1));
        }
        assertBetweenMs(turnDurationService.fakeDuration("Novinar"), 4_000, 10_000);
        // s 10 vrijede spremljena, ali najmanje 3 s
        turnDurationService.record("Novinar", Duration.ofSeconds(1));
        assertEquals(Duration.ofSeconds(3), turnDurationService.fakeDuration("Novinar"));

        // i najviše najdulji mogući potez te vrste: 13 s, a MAFIA s drugim krugom 23 s
        for (int i = 0; i < 10; i++) {
            turnDurationService.record("Policajac", Duration.ofSeconds(40));
            turnDurationService.record("MAFIA", Duration.ofSeconds(40));
        }
        assertEquals(Duration.ofSeconds(13), turnDurationService.fakeDuration("Policajac"));
        assertEquals(Duration.ofSeconds(23), turnDurationService.fakeDuration("MAFIA"));
    }

    @Test
    void onlyLast500DurationsPerTurnAreKept() {
        for (int i = 0; i < 505; i++) {
            turnDurationService.record("DAMA", Duration.ofMillis(i));
        }
        turnDurationService.record("Doktor", Duration.ofSeconds(5));

        assertEquals(500, turnDurationRepository.countByTurn("DAMA"));
        assertEquals(5L, turnDurationRepository.findAll().stream()
                .filter(duration -> duration.getTurn().equals("DAMA"))
                .mapToLong(TurnDuration::getDurationMs).min().orElseThrow());
        assertEquals(1, turnDurationRepository.countByTurn("Doktor"));
    }

    @Test
    void publicStateIsIdenticalForLivingAndDeadRole() {
        Lobby living = lobby();
        Lobby dead = lobby();
        Player livingDoctor = null;
        Player livingCitizen = null;
        Player deadCitizen = null;
        for (Lobby lobby : List.of(living, dead)) {
            Player mafia = player(lobby, "Mafia", true);
            Player doctor = player(lobby, "Doktor", lobby == living);
            player(lobby, "Policajac", true);
            Player victim = player(lobby, "Građanin", true);
            Player watcher = player(lobby, "Građanin", true);
            player(lobby, "Građanin", true);
            nightTurn(lobby, "MAFIA");
            act(mafia, "KILL", victim);
            elapse(lobby, TAIL_PASSED);
            assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
            if (lobby == living) {
                livingDoctor = doctor;
                livingCitizen = watcher;
            } else {
                deadCitizen = watcher;
            }
        }
        assertFalse(reload(living).getTurnFake());
        assertTrue(reload(dead).getTurnFake());

        assertEquals(publicView(living), publicView(dead));
        assertEquals(playerView(living, livingCitizen), playerView(dead, deadCitizen));
        // javni rok je najdulji mogući potez Doktora, a ne stvarni kraj glumljenog poteza
        assertEquals(reload(dead).getTurnStartedAt().plusSeconds(4 + 13),
                gameStateService.getPublicGameState(dead.getCode()).phaseEndsAt());

        // ni nakon što živi Doktor odigra ništa se javno ne mijenja
        act(livingDoctor, "SAVE", livingDoctor);
        assertEquals(publicView(living), publicView(dead));
        assertEquals(playerView(living, livingCitizen), playerView(dead, deadCitizen));

        // igrač na redu dobije rokove za traku vremena
        CurrentPlayerGameStateResponse doctorView = gameStateService.getCurrentPlayerState(reload(livingDoctor), reload(living));
        GameState livingState = reload(living);
        assertEquals(GameRules.actionStartedAt(livingState), doctorView.actionStartedAt());
        assertEquals(GameRules.firstRoundEndsAt(livingState), doctorView.actionEndsAt());
        assertNull(doctorView.mafiaRunoff());
    }

    // ---------- mafija ----------

    @Test
    void twoMafiaWithSameTargetEndTurnAfterTail() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        Player second = player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        List<Player> c = citizens(lobby, 3);
        nightTurn(lobby, "MAFIA");

        act(first, "KILL", c.get(0));
        // drugi još nije odabrao: rok prvog kruga
        assertEquals(GameRules.firstRoundEndsAt(reload(lobby)), reload(lobby).getPhaseEndsAt());

        // suigrači vide odabir jedan drugoga
        CurrentPlayerGameStateResponse view = gameStateService.getCurrentPlayerState(reload(second), reload(lobby));
        assertEquals(1, view.teamTargets().size());
        assertEquals(1, view.teammates().size());
        assertFalse(view.availableActions().isEmpty());

        act(second, "KILL", c.get(0));
        assertBetween(reload(lobby).getPhaseEndsAt(), 0, 4);
        assertNull(reload(lobby).getMafiaRunoffPlayerIds());
        elapse(lobby, TAIL_PASSED);
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());

        expire(lobby);
        assertFalse(reload(c.get(0)).getIsAlive());
        assertEquals("NIGHT", reload(c.get(0)).getEliminatedHow());
    }

    @Test
    void differentTargetsOpenRunoffImmediatelyAndAgreementEndsIt() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        Player second = player(lobby, "Mafia", true);
        Player doctor = player(lobby, "Doktor", true);
        List<Player> c = citizens(lobby, 3);
        nightTurn(lobby, "MAFIA");

        act(first, "KILL", c.get(0));
        act(second, "KILL", c.get(1));

        // drugi krug odmah, ne tek na isteku prvog
        GameState state = reload(lobby);
        assertEquals("MAFIA", state.getCurrentTurnRole());
        assertBetween(state.getMafiaRunoffEndsAt(), 9, 10);
        assertEquals(state.getMafiaRunoffEndsAt(), state.getPhaseEndsAt());

        List<Long> candidates = ids(c.get(0), c.get(1));
        CurrentPlayerGameStateResponse view = gameStateService.getCurrentPlayerState(reload(first), state);
        assertNotNull(view.mafiaRunoff());
        assertTrue(view.mafiaRunoff().active());
        assertEquals(candidates, view.mafiaRunoff().candidatePlayerIds());
        assertEquals(state.getMafiaRunoffEndsAt(), view.mafiaRunoff().endsAt());
        assertEquals(candidates, view.availableActions().getFirst().targetPlayerIds());
        assertEquals(state.getMafiaRunoffEndsAt(), view.actionEndsAt());
        // drugi krug vidi samo tim mafije
        assertNull(gameStateService.getCurrentPlayerState(reload(doctor), state).mafiaRunoff());
        assertNull(gameStateService.getCurrentPlayerState(reload(c.get(2)), state).mafiaRunoff());

        // samo kandidati iz prvog kruga
        assertCode(ErrorCode.INVALID_TARGET, () -> act(first, "KILL", c.get(2)));

        act(second, "KILL", c.get(0));
        assertBetween(reload(lobby).getPhaseEndsAt(), 0, 4);
        elapse(lobby, TAIL_PASSED);
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
        assertNull(gameStateService.getCurrentPlayerState(reload(first), reload(lobby)).mafiaRunoff());

        expire(lobby);
        assertFalse(reload(c.get(0)).getIsAlive());
        assertTrue(reload(c.get(1)).getIsAlive());
    }

    @Test
    void runoffWithoutAgreementPicksRandomCandidate() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        Player second = player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        List<Player> c = citizens(lobby, 4);
        nightTurn(lobby, "MAFIA");
        // kandidati su [c1, c2] po id-u; nasumični odabir uzme drugog
        fixRandomPick(1);

        act(first, "KILL", c.get(1));
        act(second, "KILL", c.get(2));
        elapse(lobby, Duration.ofSeconds(9));
        assertEquals("MAFIA", reload(lobby).getCurrentTurnRole());
        elapse(lobby, Duration.ofMillis(1_100));
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
        assertEquals(c.get(2).getId(), reload(lobby).getNightMafiaTargetId());

        expire(lobby);
        assertFalse(reload(c.get(2)).getIsAlive());
        assertTrue(Stream.of(c.get(0), c.get(1), c.get(3)).allMatch(player -> reload(player).getIsAlive()));
        // potez s drugim krugom pamti se cijeli: 10 s drugog kruga (otvoren odmah na početku odabira)
        long mafiaMs = turnDurationRepository.findAll().stream()
                .filter(duration -> duration.getTurn().equals("MAFIA"))
                .mapToLong(TurnDuration::getDurationMs).findFirst().orElseThrow();
        assertTrue(mafiaMs >= 10_000 && mafiaMs <= 10_500, "potez mafije " + mafiaMs + " ms");
    }

    @Test
    void onlyOneMafiaChoseByDeadlineAndHisTargetCounts() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        List<Player> c = citizens(lobby, 3);
        nightTurn(lobby, "MAFIA");

        act(first, "KILL", c.get(1));
        elapse(lobby, Duration.ofMillis(13_100));
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());

        expire(lobby);
        assertFalse(reload(c.get(1)).getIsAlive());
    }

    @Test
    void differentTargetsAtDeadlineOpenRunoffEvenIfNotEveryoneChose() {
        Lobby lobby = lobby();
        Player first = player(lobby, "Mafia", true);
        Player second = player(lobby, "Mafia", true);
        Player third = player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        List<Player> c = citizens(lobby, 4);
        nightTurn(lobby, "MAFIA");

        act(first, "KILL", c.get(0));
        act(second, "KILL", c.get(1));
        assertNull(reload(lobby).getMafiaRunoffPlayerIds());

        expire(lobby);
        GameState state = reload(lobby);
        assertEquals("MAFIA", state.getCurrentTurnRole());
        assertEquals(joinedIds(c.get(0), c.get(1)), state.getMafiaRunoffPlayerIds());
        assertBetween(state.getPhaseEndsAt(), 9, 10);

        assertCode(ErrorCode.INVALID_TARGET, () -> act(third, "KILL", c.get(2)));
        act(third, "KILL", c.get(0));
        assertEquals(state.getMafiaRunoffEndsAt(), reload(lobby).getPhaseEndsAt());
        act(second, "KILL", c.get(0));
        assertBetween(reload(lobby).getPhaseEndsAt(), 0, 4);
    }

    @Test
    void nobodyChoseKillsRandomAlivePlayerEvenFromMafia() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Doktor", true);
        citizens(lobby, 3);
        nightTurn(lobby, "MAFIA");
        // živi po id-u: Mafija je prva
        fixRandomPick(0);

        expire(lobby);
        assertEquals("Doktor", reload(lobby).getCurrentTurnRole());
        expire(lobby);

        // strada sama Mafija, pa provjera pobjednika radi kao i inače
        assertFalse(reload(mafia).getIsAlive());
        assertEquals("NIGHT", reload(mafia).getEliminatedHow());
        PublicGameStateResponse publicState = gameStateService.getPublicGameState(lobby.getCode());
        assertEquals("FINISHED", publicState.phase());
        assertEquals("CITIZENS_WIN", publicState.winner());
    }

    @Test
    void doctorCanSaveRandomMafiaTarget() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        Player doctor = player(lobby, "Doktor", true);
        citizens(lobby, 3);
        nightTurn(lobby, "MAFIA");
        fixRandomPick(0);

        expire(lobby);
        act(doctor, "SAVE", mafia);
        expire(lobby);

        assertTrue(reload(mafia).getIsAlive());
        PublicGameStateResponse publicState = gameStateService.getPublicGameState(lobby.getCode());
        assertEquals("DAY", publicState.phase());
        // jutarnji izvještaj ne razlikuje odabranu i nasumičnu metu: samo tko je stradao
        assertEquals(List.of(), publicState.lastNight().killedPlayerIds());
    }

    @Test
    void mafiaCannotTargetOwnTeam() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        Player dama = player(lobby, "Dama", true);
        citizens(lobby, 3);
        nightTurn(lobby, "MAFIA");

        assertCode(ErrorCode.INVALID_TARGET, () -> act(mafia, "KILL", dama));
    }

    // ---------- Dama ----------

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

        // ista pravila kao za mafiju: odabir sama → kraj nakon pauze
        act(dama, "KILL", citizen);
        assertBetween(reload(lobby).getPhaseEndsAt(), 0, 4);
        elapse(lobby, TAIL_PASSED);
        GameState state = reload(lobby);
        assertEquals("DAMA", state.getCurrentTurnRole());
        assertTrue(state.getTurnFake());
        // 4 s naracije + glumljeni odabir 4–10 s
        assertBetween(state.getPhaseEndsAt(), 7, 14);

        expire(lobby);
        assertFalse(reload(citizen).getIsAlive());
    }

    @Test
    void damaCanSilenceAnyAlivePlayer() {
        Lobby lobby = lobby();
        Player mafia = player(lobby, "Mafia", true);
        Player dama = player(lobby, "Dama", true);
        Player target = player(lobby, "Građanin", true);
        Player other = player(lobby, "Građanin", true);
        Player third = player(lobby, "Građanin", true);
        Player fourth = player(lobby, "Građanin", true);
        nightTurn(lobby, "MAFIA");

        act(mafia, "KILL", target);
        expire(lobby);
        assertEquals("DAMA", reload(lobby).getCurrentTurnRole());

        // svi živi: i ona sama, i Mafija, i meta mafije
        List<Long> allowed = gameStateService.getCurrentPlayerState(dama, reload(lobby))
                .availableActions().getFirst().targetPlayerIds();
        assertEquals(ids(mafia, dama, target, other, third, fourth), allowed);

        // meta mafije se smije utišati; kako stradava, ne ostaje utišana
        act(dama, "SILENCE", target);
        expire(lobby);
        PublicGameStateResponse publicState = gameStateService.getPublicGameState(lobby.getCode());
        assertEquals(List.of(target.getId()), publicState.nightKilledPlayerIds());
        assertEquals(List.of(), publicState.silencedPlayerIds());
        assertEquals(List.of(), publicState.lastNight().silencedPlayerIds());
    }

    @Test
    void damaCanSilenceHerselfOrMafia() {
        for (boolean self : new boolean[]{true, false}) {
            Lobby lobby = lobby();
            Player mafia = player(lobby, "Mafia", true);
            Player dama = player(lobby, "Dama", true);
            List<Player> c = citizens(lobby, 4);
            nightTurn(lobby, "MAFIA");

            act(mafia, "KILL", c.getFirst());
            expire(lobby);
            assertEquals("DAMA", reload(lobby).getCurrentTurnRole());
            Player silenced = self ? dama : mafia;
            act(dama, "SILENCE", silenced);
            expire(lobby);

            PublicGameStateResponse publicState = gameStateService.getPublicGameState(lobby.getCode());
            assertEquals("DAY", publicState.phase());
            assertEquals(List.of(silenced.getId()), publicState.silencedPlayerIds());
            assertEquals(List.of(silenced.getId()), publicState.lastNight().silencedPlayerIds());
            assertEquals(List.of(c.getFirst().getId()), publicState.lastNight().killedPlayerIds());
        }
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

    // ---------- pomoćno ----------

    /** Potez uloge kojoj je naracija upravo završila (odabir počinje sada) */
    private void nightTurn(Lobby lobby, String turn) {
        GameState state = state(lobby, "NIGHT", 1);
        state.setCurrentTurnRole(turn);
        state.setTurnStartedAt(OffsetDateTime.now().minus(GameRules.TURN_NARRATION));
        state.setTurnFake(false);
        state.setPhaseEndsAt(GameRules.firstRoundEndsAt(state));
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

    private List<Player> citizens(Lobby lobby, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> player(lobby, "Građanin", true)).toList();
    }

    private static List<Long> ids(Player... players) {
        return Stream.of(players).map(Player::getId).sorted().toList();
    }

    /** Glumljeni odabir: od kraja naracije do stvarnog kraja poteza */
    private static Duration fakeTurnLength(GameState state) {
        return Duration.between(GameRules.actionStartedAt(state), state.getPhaseEndsAt());
    }

    private static void assertBetweenMs(Duration duration, long minMs, long maxMs) {
        assertTrue(duration.toMillis() >= minMs && duration.toMillis() <= maxMs, "trajanje " + duration);
    }

    /** Javno stanje bez onoga što se razlikuje po sobi (kod, igrači, sat); rok relativno na početak poteza */
    private List<Object> publicView(Lobby lobby) {
        PublicGameStateResponse state = gameStateService.getPublicGameState(lobby.getCode());
        OffsetDateTime turnStart = reload(lobby).getTurnStartedAt();
        return Arrays.asList(state.lobbyStatus(), state.phase(), state.roundNumber(), state.gameFinished(),
                state.winner(), state.votingOpen(), state.tiedPlayerIds(),
                Duration.between(turnStart, state.phaseEndsAt()), state.silencedPlayerIds(),
                state.nightKilledPlayerIds(), state.voting(), state.currentTurnRole(), state.lastNight(),
                state.readyPlayerIds());
    }

    /** game-state igrača koji nije na redu, bez onoga što se razlikuje po sobi */
    private List<Object> playerView(Lobby lobby, Player player) {
        GameStateResponse state = gameStateService.getGameState(lobby.getCode(), player.getId(), player.getPlayerToken());
        OffsetDateTime turnStart = reload(lobby).getTurnStartedAt();
        CurrentPlayerGameStateResponse me = state.currentPlayer();
        return Arrays.asList(state.phase(), state.currentTurnRole(), Duration.between(turnStart, state.phaseEndsAt()),
                state.voting(), state.lastNight(), state.readyPlayerIds(), state.silencedPlayerIds(),
                me.alive(), me.role(), me.canAct(), me.hasActed(), me.availableActions(), me.actionEndsAt(),
                me.actionStartedAt(), me.teammates(), me.teamTargets(), me.mafiaRunoff());
    }

    /** Kandidati kako ih sprema game_state: id-evi po redu, odvojeni zarezom */
    private static String joinedIds(Player... players) {
        return String.join(",", ids(players).stream().map(String::valueOf).toList());
    }
}
