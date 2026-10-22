package com.niko.ai_mafija_backend;

import com.jayway.jsonpath.JsonPath;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.niko.ai_mafija_backend.exception.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cijela igra preko REST-a (MockMvc), istim zahtjevima kao frontend.
 * Uloge server dijeli nasumično, pa ih test pročita iz game-state; istek roka se glumi s timeout().
 */
@AutoConfigureMockMvc
class FullGameApiTests extends GameTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private final Map<String, Long> roleIds = new HashMap<>();

    private String code;
    private Seat host;
    private final List<Seat> seats = new ArrayList<>();
    private final Map<String, List<Seat>> byRole = new HashMap<>();

    private record Seat(long id, String token) {
    }

    @BeforeEach
    void createRoles() {
        for (String name : List.of("Mafia", "Građanin", "Policajac", "Doktor", "Novinar", "Dama")) {
            roleIds.put(name, roleRepository.save(Role.builder().name(name).opis("Opis: " + name).build()).getId());
        }
    }

    // ---------- cijela igra ----------

    @Test
    void fullGameWithAllRolesUntilCitizensWin() throws Exception {
        createAndStart(Map.of("Mafia", 1, "Dama", 1, "Doktor", 1, "Policajac", 1, "Novinar", 1, "Građanin", 3));
        Seat mafia = role("Mafia");
        Seat dama = role("Dama");
        Seat doktor = role("Doktor");
        Seat policajac = role("Policajac");
        Seat novinar = role("Novinar");
        Seat g1 = byRole.get("Građanin").get(0);
        Seat g2 = byRole.get("Građanin").get(1);
        Seat g3 = byRole.get("Građanin").get(2);

        // ---------- noć 1 ----------
        String state = gameState(host);
        assertEquals("NIGHT", read(state, "$.phase"));
        assertEquals(1, (int) read(state, "$.roundNumber"));
        assertNull(read(state, "$.currentTurnRole"));
        // uvod noći: još nitko nije na redu
        expectError(act(doktor, "SAVE", doktor, null), HttpStatus.FORBIDDEN, NOT_YOUR_TURN);

        timeout();
        assertEquals("MAFIA", turn());
        String mafiaView = gameState(mafia);
        assertTrue(bool(mafiaView, "$.currentPlayer.canAct"));
        assertEquals("KILL", read(mafiaView, "$.currentPlayer.availableActions[0].type"));
        List<Long> killTargets = longs(read(mafiaView, "$.currentPlayer.availableActions[0].targetPlayerIds"));
        assertEquals(6, killTargets.size());
        assertFalse(killTargets.contains(mafia.id()));
        assertFalse(killTargets.contains(dama.id()));
        expectError(act(doktor, "SAVE", doktor, null), HttpStatus.FORBIDDEN, NOT_YOUR_TURN);
        expectError(act(mafia, "KILL", dama, null), HttpStatus.CONFLICT, INVALID_TARGET);
        ok(act(mafia, "KILL", g1, null));

        timeout();
        assertEquals("DAMA", turn());
        expectError(act(dama, "SILENCE", g1, null), HttpStatus.CONFLICT, INVALID_TARGET);
        ok(act(dama, "SILENCE", g2, null));
        expectError(act(dama, "SILENCE", g3, null), HttpStatus.CONFLICT, ALREADY_ACTED);

        timeout();
        assertEquals("Doktor", turn());
        ok(act(doktor, "SAVE", doktor, null));

        timeout();
        assertEquals("Policajac", turn());
        assertEquals("MAFIA", read(ok(act(policajac, "INVESTIGATE", mafia, null)), "$.investigationResult"));

        timeout();
        assertEquals("Novinar", turn());
        assertEquals("ISTI_TIM", read(ok(act(novinar, "COMPARE", mafia, dama)), "$.journalistResult"));

        timeout();
        state = gameState(host);
        assertEquals("DAY", read(state, "$.phase"));
        assertEquals(1, (int) read(state, "$.roundNumber"));
        assertEquals(List.of(g1.id()), longs(read(state, "$.nightKilledPlayerIds")));
        assertEquals(List.of(g2.id()), longs(read(state, "$.silencedPlayerIds")));
        assertFalse(alive(state, g1));
        assertEquals("NIGHT", playerField(state, g1, "eliminatedHow"));

        // ---------- dan 1: glasanje s utišanim, izjednačenje, ponovno glasanje ----------
        expectError(castVote(mafia, g3), HttpStatus.CONFLICT, WRONG_PHASE);
        Seat notHost = seats.stream().filter(seat -> !seat.equals(host)).findFirst().orElseThrow();
        expectError(post(lobbyPath() + "/voting/start", auth(notHost)), HttpStatus.FORBIDDEN, NOT_HOST);
        ok(post(lobbyPath() + "/voting/start", auth(host)));
        assertEquals("VOTING", read(gameState(host), "$.phase"));

        // utišani ne glasa (i ne čeka ga se), mrtvi isto
        String silencedView = gameState(g2);
        assertFalse(bool(silencedView, "$.voting.canVote"));
        assertFalse(longs(read(silencedView, "$.voting.eligibleVoterIds")).contains(g2.id()));
        expectError(castVote(g2, mafia), HttpStatus.FORBIDDEN, SILENCED);
        expectError(castVote(g1, mafia), HttpStatus.FORBIDDEN, DEAD_PLAYER);

        ok(castVote(doktor, mafia));
        ok(castVote(policajac, mafia));
        ok(castVote(g3, mafia));
        ok(castVote(mafia, g3));
        ok(castVote(dama, g3));
        String tie = ok(castVote(novinar, g3));
        assertTrue(bool(tie, "$.votingResolved"));
        assertEquals("TIE", read(tie, "$.resolveResult.result"));
        assertEquals(ids(mafia, g3), longs(read(tie, "$.resolveResult.tiedPlayerIds")));

        // u TIE_BREAK glasaju samo neizjednačeni (i neutišani), i to samo za izjednačene
        String tieView = gameState(doktor);
        assertEquals("TIE_BREAK", read(tieView, "$.phase"));
        assertEquals(ids(dama, doktor, policajac, novinar), longs(read(tieView, "$.voting.eligibleVoterIds")));
        assertEquals(ids(mafia, g3), longs(read(tieView, "$.voting.eligibleTargetPlayerIds")));
        assertFalse(bool(tieView, "$.voting.canAbstain"));
        expectError(castVote(mafia, g3), HttpStatus.FORBIDDEN, TIED_PLAYER_CANNOT_VOTE);
        expectError(castVote(g3, mafia), HttpStatus.FORBIDDEN, TIED_PLAYER_CANNOT_VOTE);
        expectError(castVote(g2, mafia), HttpStatus.FORBIDDEN, SILENCED);
        expectError(castVote(doktor, null), HttpStatus.BAD_REQUEST, ABSTAIN_NOT_ALLOWED);
        expectError(castVote(doktor, novinar), HttpStatus.FORBIDDEN, INVALID_TARGET);

        ok(castVote(doktor, mafia));
        ok(castVote(policajac, mafia));
        ok(castVote(novinar, mafia));
        String eliminated = ok(castVote(dama, g3));
        assertEquals("ELIMINATED", read(eliminated, "$.resolveResult.result"));
        assertEquals(mafia.id(), ((Number) read(eliminated, "$.resolveResult.eliminatedPlayerId")).longValue());

        state = gameState(host);
        assertEquals("NIGHT", read(state, "$.phase"));
        assertEquals(2, (int) read(state, "$.roundNumber"));
        assertEquals("VOTE", playerField(state, mafia, "eliminatedHow"));

        // ---------- noć 2: Mafije nema, Dama ubija ----------
        timeout();
        assertEquals("MAFIA", turn());
        String damaView = gameState(dama);
        assertTrue(bool(damaView, "$.currentPlayer.canAct"));
        assertEquals("KILL", read(damaView, "$.currentPlayer.availableActions[0].type"));
        assertFalse(longs(read(damaView, "$.currentPlayer.availableActions[0].targetPlayerIds")).contains(dama.id()));
        ok(act(dama, "KILL", g3, null));

        // Damin potez se glumi (traje jednako), ali ona više ne utišava
        timeout();
        assertEquals("DAMA", turn());
        assertFalse(bool(gameState(dama), "$.currentPlayer.canAct"));
        expectError(act(dama, "SILENCE", g2, null), HttpStatus.FORBIDDEN, NOT_YOUR_TURN);

        timeout();
        assertEquals("Doktor", turn());
        ok(act(doktor, "SAVE", policajac, null));

        timeout();
        assertEquals("Policajac", turn());
        // bez Mafije Dama više ne izgleda nevino
        assertEquals("MAFIA", read(ok(act(policajac, "INVESTIGATE", dama, null)), "$.investigationResult"));

        timeout();
        assertEquals("Novinar", turn());
        assertEquals("RAZLICIT_TIM", read(ok(act(novinar, "COMPARE", dama, g2)), "$.journalistResult"));

        timeout();
        state = gameState(host);
        assertEquals("DAY", read(state, "$.phase"));
        assertEquals(2, (int) read(state, "$.roundNumber"));
        assertEquals(List.of(g3.id()), longs(read(state, "$.nightKilledPlayerIds")));
        assertEquals(List.of(), longs(read(state, "$.silencedPlayerIds")));
        // Dama sama protiv četvero: igra traje
        assertFalse(bool(state, "$.gameFinished"));
        assertNull(read(state, "$.winner"));

        // ---------- dan 2: rok rasprave istekne, Dama ispada ----------
        timeout();
        assertEquals("VOTING", read(gameState(host), "$.phase"));
        ok(castVote(doktor, dama));
        ok(castVote(policajac, dama));
        ok(castVote(novinar, dama));
        ok(castVote(dama, g2));
        String end = ok(castVote(g2, dama));
        assertEquals("CITIZENS_WIN", read(end, "$.resolveResult.result"));

        state = gameState(host);
        assertEquals("FINISHED", read(state, "$.phase"));
        assertEquals("FINISHED", read(state, "$.lobbyStatus"));
        assertTrue(bool(state, "$.gameFinished"));
        assertEquals("CITIZENS_WIN", read(state, "$.winner"));
        // kad igra završi, uloge su javne
        List<String> revealed = read(state, "$.players[*].roleName");
        assertEquals(8, revealed.size());
        assertTrue(revealed.stream().allMatch(name -> name != null));
        expectError(castVote(doktor, g2), HttpStatus.CONFLICT, GAME_FINISHED);
    }

    @Test
    void damaAloneAgainstOneCitizenWinsAtNight() throws Exception {
        createAndStart(Map.of("Mafia", 1, "Dama", 1, "Građanin", 3));
        Seat mafia = role("Mafia");
        Seat dama = role("Dama");
        Seat g1 = byRole.get("Građanin").get(0);
        Seat g2 = byRole.get("Građanin").get(1);
        Seat g3 = byRole.get("Građanin").get(2);

        // noć 1: nitko ne odigra (tko ne odigra, gubi potez); uloge kojih nema (Doktor…) se preskaču
        timeout();
        assertEquals("MAFIA", turn());
        timeout();
        assertEquals("DAMA", turn());
        timeout();
        String state = gameState(host);
        assertEquals("DAY", read(state, "$.phase"));
        assertEquals(List.of(), longs(read(state, "$.nightKilledPlayerIds")));

        // dan 1: Mafija ispada, Dama ostaje sama protiv troje
        timeout();
        ok(castVote(g1, mafia));
        ok(castVote(g2, mafia));
        ok(castVote(g3, mafia));
        ok(castVote(dama, g1));
        assertEquals("ELIMINATED", read(ok(castVote(mafia, g1)), "$.resolveResult.result"));
        assertEquals("NIGHT", read(gameState(host), "$.phase"));

        // noć 2: Dama dobiva KILL, noć ide dalje kroz glumljeni Damin potez
        damaKills(dama, g1);
        state = gameState(host);
        assertEquals("DAY", read(state, "$.phase"));
        assertEquals(List.of(g1.id()), longs(read(state, "$.nightKilledPlayerIds")));
        // Dama protiv dvoje: igra traje
        assertFalse(bool(state, "$.gameFinished"));

        // dan 2: svi suzdržani, nitko ne ispada
        timeout();
        ok(castVote(dama, null));
        ok(castVote(g2, null));
        assertEquals("NO_ELIMINATION", read(ok(castVote(g3, null)), "$.resolveResult.result"));

        // noć 3: Dama ubija i ostaje sama protiv jednog građanina = pobjeda mafije
        damaKills(dama, g2);
        state = gameState(host);
        assertFalse(alive(state, g2));
        assertTrue(alive(state, g3));
        assertEquals("FINISHED", read(state, "$.phase"));
        assertTrue(bool(state, "$.gameFinished"));
        assertEquals("MAFIA_WIN", read(state, "$.winner"));
    }

    /** Noć bez Mafije: uvod → potez MAFIA (Dama ubija) → glumljeni potez DAMA → obrada noći */
    private void damaKills(Seat dama, Seat victim) throws Exception {
        timeout();
        assertEquals("MAFIA", turn());
        String damaView = gameState(dama);
        assertTrue(bool(damaView, "$.currentPlayer.canAct"));
        assertEquals("KILL", read(damaView, "$.currentPlayer.availableActions[0].type"));
        ok(act(dama, "KILL", victim, null));
        timeout();
        assertEquals("DAMA", turn());
        assertEquals("NIGHT", read(gameState(host), "$.phase"));
        assertFalse(bool(gameState(dama), "$.currentPlayer.canAct"));
        timeout();
    }

    // ---------- soba i kodovi grešaka ----------

    @Test
    void lobbyErrorsCarryStableCodes() throws Exception {
        createLobby(Map.of("Mafia", 1, "Građanin", 3));

        expectError(post(lobbyPath() + "/start", auth(host)), HttpStatus.CONFLICT, LOBBY_NOT_FULL);
        expectError(join("domaćin"), HttpStatus.CONFLICT, NICKNAME_TAKEN);
        expectError(post("/api/lobbies/NEMA00/join", Map.of("nickname", "Ana")), HttpStatus.NOT_FOUND, LOBBY_NOT_FOUND);

        joinAll(4);
        expectError(join("Višak"), HttpStatus.CONFLICT, LOBBY_FULL);
        expectError(post(lobbyPath() + "/start", auth(seats.get(1))), HttpStatus.FORBIDDEN, NOT_HOST);
        expectError(mvc.perform(get(lobbyPath() + "/game-state")
                .header("X-Player-Id", host.id())
                .header("X-Player-Token", "kriv-token")), HttpStatus.UNAUTHORIZED, INVALID_TOKEN);

        ok(post(lobbyPath() + "/start", auth(host)));
        expectError(post(lobbyPath() + "/start", auth(host)), HttpStatus.CONFLICT, GAME_ALREADY_STARTED);
        // postojeća polja odgovora o grešci ostaju
        join("Kasni")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_ALREADY_STARTED"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(lobbyPath() + "/join"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void nicknameIsRequiredAndAtMost16Characters() throws Exception {
        String sixteen = "A".repeat(16);
        String seventeen = "A".repeat(17);

        for (String bad : new String[]{"", "   ", seventeen}) {
            expectError(post("/api/lobbies", lobbyBody(bad, Map.of("Mafia", 1, "Građanin", 3))),
                    HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
        }
        Map<String, Object> missing = new HashMap<>(lobbyBody(null, Map.of("Mafia", 1, "Građanin", 3)));
        missing.remove("hostNickname");
        expectError(post("/api/lobbies", missing), HttpStatus.BAD_REQUEST, VALIDATION_ERROR);

        createLobby(Map.of("Mafia", 1, "Građanin", 3), sixteen);
        for (String bad : new String[]{"", "   ", seventeen}) {
            expectError(join(bad), HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
        }
        expectError(post(lobbyPath() + "/join", Map.of()), HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
        ok(join("B".repeat(16)));
    }

    @Test
    void publicRolesEndpointMatchesTestEndpoint() throws Exception {
        String roles = ok(mvc.perform(get("/api/roles")));
        String testRoles = ok(mvc.perform(get("/api/test/roles")));

        List<?> list = json.readValue(roles, List.class);
        assertEquals(6, list.size());
        assertEquals(new HashSet<>(json.readValue(testRoles, List.class)), new HashSet<>(list));
        assertEquals(roleIds.values().stream().sorted().toList(), longs(read(roles, "$[*].id")));
        assertEquals(List.of("Opis: Mafia"), read(roles, "$[?(@.name == 'Mafia')].opis"));
    }

    // ---------- soba ----------

    private void createAndStart(Map<String, Integer> composition) throws Exception {
        createLobby(composition);
        joinAll(composition.values().stream().mapToInt(Integer::intValue).sum());
        ok(post(lobbyPath() + "/start", auth(host)));
        for (Seat seat : seats) {
            String roleName = read(gameState(seat), "$.currentPlayer.role");
            byRole.computeIfAbsent(roleName, name -> new ArrayList<>()).add(seat);
        }
    }

    private void createLobby(Map<String, Integer> composition) throws Exception {
        createLobby(composition, "Domaćin");
    }

    private void createLobby(Map<String, Integer> composition, String hostNickname) throws Exception {
        String created = ok(post("/api/lobbies", lobbyBody(hostNickname, composition)));
        code = read(created, "$.code");
        host = new Seat(((Number) read(created, "$.hostPlayerId")).longValue(), read(created, "$.playerToken"));
        seats.add(host);
    }

    private Map<String, Object> lobbyBody(String hostNickname, Map<String, Integer> composition) {
        List<Map<String, Object>> roles = composition.entrySet().stream()
                .map(entry -> Map.<String, Object>of("roleId", roleIds.get(entry.getKey()), "count", entry.getValue()))
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("hostNickname", hostNickname);
        body.put("maxPlayers", composition.values().stream().mapToInt(Integer::intValue).sum());
        body.put("roles", roles);
        return body;
    }

    private void joinAll(int total) throws Exception {
        for (int i = seats.size(); i < total; i++) {
            String joined = ok(join("Igrač" + i));
            seats.add(new Seat(((Number) read(joined, "$.playerId")).longValue(), read(joined, "$.playerToken")));
        }
    }

    private ResultActions join(String nickname) throws Exception {
        return post(lobbyPath() + "/join", Map.of("nickname", nickname));
    }

    private Seat role(String roleName) {
        return byRole.get(roleName).getFirst();
    }

    private String lobbyPath() {
        return "/api/lobbies/" + code;
    }

    // ---------- igra ----------

    /** Glumi istek roka trenutne faze ili poteza (timer je u testovima isključen) */
    private void timeout() {
        expire(lobbyRepository.findByCode(code).orElseThrow());
    }

    private String gameState(Seat seat) throws Exception {
        return ok(mvc.perform(get(lobbyPath() + "/game-state")
                .header("X-Player-Id", seat.id())
                .header("X-Player-Token", seat.token())));
    }

    private String turn() throws Exception {
        return read(gameState(host), "$.currentTurnRole");
    }

    private ResultActions act(Seat actor, String actionType, Seat target, Seat secondTarget) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("playerId", actor.id());
        body.put("playerToken", actor.token());
        body.put("actionType", actionType);
        body.put("targetPlayerId", target.id());
        body.put("secondTargetPlayerId", secondTarget != null ? secondTarget.id() : null);
        return post("/api/actions", body);
    }

    /** target null = "Suzdržan" */
    private ResultActions castVote(Seat voter, Seat target) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("voterPlayerId", voter.id());
        body.put("playerToken", voter.token());
        body.put("targetPlayerId", target != null ? target.id() : null);
        return post("/api/votes", body);
    }

    private Map<String, Object> auth(Seat seat) {
        return Map.of("playerId", seat.id(), "playerToken", seat.token());
    }

    private boolean alive(String state, Seat seat) {
        return Boolean.TRUE.equals(playerField(state, seat, "alive"));
    }

    private Object playerField(String state, Seat seat, String field) {
        List<Object> values = JsonPath.read(state, "$.players[?(@.id == " + seat.id() + ")]." + field);
        return values.getFirst();
    }

    // ---------- HTTP i JSON ----------

    private ResultActions post(String path, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private static String ok(ResultActions result) throws Exception {
        return result.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static void expectError(ResultActions result, HttpStatus httpStatus, ErrorCode code) throws Exception {
        result.andExpect(status().is(httpStatus.value()))
                .andExpect(jsonPath("$.code").value(code.name()));
    }

    private static <T> T read(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static boolean bool(String body, String path) {
        return Boolean.TRUE.equals(JsonPath.read(body, path));
    }

    private static List<Long> longs(Object values) {
        return ((List<?>) values).stream().map(value -> ((Number) value).longValue()).toList();
    }

    private static List<Long> ids(Seat... players) {
        return Stream.of(players).map(Seat::id).sorted(Comparator.naturalOrder()).toList();
    }
}
