package com.niko.ai_mafija_backend;

import com.jayway.jsonpath.JsonPath;
import com.niko.ai_mafija_backend.dto.Response.HostChangedResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerLeftResponse;
import com.niko.ai_mafija_backend.dto.WebSocket.LobbyEventResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
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
    @MockitoSpyBean private SimpMessagingTemplate messagingTemplate;

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

    // ---------- postavke sobe, avatar, domaćin, javne sobe, izlazak ----------

    @Test
    void lobbySettingsHaveDefaultsForOldClientsAndCanBeSet() throws Exception {
        // stari klijent: nijedno novo polje nije poslano
        createLobby(Map.of("Mafia", 1, "Građanin", 3));
        String details = lobbyDetails(code);
        assertEquals("LIVE", read(details, "$.mode"));
        assertFalse(bool(details, "$.isPublic"));
        assertTrue(bool(details, "$.voiceEnabled"));
        assertEquals(host.id(), ((Number) read(details, "$.hostPlayerId")).longValue());
        assertNull(playerField(details, host, "avatarEmoji"));
        assertTrue(bool(details, "$.players[0].host"));
        // sastav uloga
        List<String> roleNames = read(details, "$.roles[*].roleName");
        assertEquals(List.of("Građanin", "Mafia"), roleNames.stream().sorted().toList());
        List<Number> counts = read(details, "$.roles[*].count");
        assertEquals(4, counts.stream().mapToInt(Number::intValue).sum());
        List<Number> mafiaRoleId = read(details, "$.roles[?(@.roleName == 'Mafia')].roleId");
        assertEquals(roleIds.get("Mafia"), mafiaRoleId.getFirst().longValue());

        String custom = create("Niko", Map.of("mode", "ONLINE", "isPublic", true, "voiceEnabled", false,
                "hostAvatarEmoji", "🦊"));
        details = lobbyDetails(read(custom, "$.code"));
        assertEquals("ONLINE", read(details, "$.mode"));
        assertTrue(bool(details, "$.isPublic"));
        assertFalse(bool(details, "$.voiceEnabled"));
        assertEquals("🦊", read(details, "$.players[0].avatarEmoji"));
    }

    @Test
    void invalidSettingsAndAvatarsAreRejected() throws Exception {
        expectError(post("/api/lobbies", lobbyBody("Niko", Map.of("Mafia", 1, "Građanin", 3), Map.of("mode", "PUTEM"))),
                HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
        expectError(post("/api/lobbies", lobbyBody("Niko", Map.of("Mafia", 1, "Građanin", 3),
                Map.of("hostAvatarEmoji", "A".repeat(9)))), HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
        // točno 8 znakova prolazi; prazan avatar znači "bez avatara"
        create("Niko", Map.of("hostAvatarEmoji", "A".repeat(8)));
        String blank = create("Prazno", Map.of("hostAvatarEmoji", "  "));
        assertNull(playerField(lobbyDetails(read(blank, "$.code")), seatOf(blank, "$.hostPlayerId"), "avatarEmoji"));

        createLobby(Map.of("Mafia", 1, "Građanin", 3));
        expectError(post(lobbyPath() + "/join", Map.of("nickname", "Ana", "avatarEmoji", "A".repeat(9))),
                HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
    }

    @Test
    void avatarsAreVisibleInLobbyPlayersAndGameState() throws Exception {
        String created = create("Niko", Map.of("hostAvatarEmoji", "🦊"));
        code = read(created, "$.code");
        host = seatOf(created, "$.hostPlayerId");
        seats.add(host);
        String joined = ok(post(lobbyPath() + "/join", Map.of("nickname", "Ana", "avatarEmoji", "🐼")));
        Seat ana = new Seat(((Number) read(joined, "$.playerId")).longValue(), read(joined, "$.playerToken"));
        seats.add(ana);
        joinAll(4);

        assertEquals("🐼", playerField(lobbyDetails(code), ana, "avatarEmoji"));
        String players = ok(mvc.perform(get(lobbyPath() + "/players")));
        assertEquals("🦊", playerFieldOfList(players, host, "avatarEmoji"));
        assertEquals("🐼", playerFieldOfList(players, ana, "avatarEmoji"));
        assertNull(playerFieldOfList(players, seats.getLast(), "avatarEmoji"));

        ok(post(lobbyPath() + "/start", auth(host)));
        // igra je počela: i privatno i javno stanje (WebSocket) nose avatar
        assertEquals("🐼", playerField(gameState(host), ana, "avatarEmoji"));
        assertEquals("🦊", playerField(gameState(ana), host, "avatarEmoji"));
        assertEquals("🐼", gameStateService.getPublicGameState(code).players().stream()
                .filter(player -> player.getId().equals(ana.id())).findFirst().orElseThrow().getAvatarEmoji());
    }

    @Test
    void publicLobbiesListOnlyWaitingPublicRoomsNewestFirst() throws Exception {
        String alfa = create("Alfa", Map.of("isPublic", true, "hostAvatarEmoji", "🦊"));
        create("Beta", Map.of());
        String gama = create("Gama", Map.of("isPublic", true));
        String delta = create("Delta", Map.of("isPublic", true, "mode", "ONLINE"));

        ok(post("/api/lobbies/" + read(alfa, "$.code") + "/join", Map.of("nickname", "Gost")));
        // Gama je javna, ali igra je počela, pa ne smije biti na popisu
        String gamaCode = read(gama, "$.code");
        for (int i = 0; i < 3; i++) {
            ok(post("/api/lobbies/" + gamaCode + "/join", Map.of("nickname", "G" + i)));
        }
        ok(post("/api/lobbies/" + gamaCode + "/start", auth(seatOf(gama, "$.hostPlayerId"))));

        String list = ok(mvc.perform(get("/api/lobbies").param("public", "true")));
        assertEquals(List.of(read(delta, "$.code"), read(alfa, "$.code")), read(list, "$[*].code"));
        assertEquals("Delta", read(list, "$[0].hostNickname"));
        assertNull(read(list, "$[0].hostAvatarEmoji"));
        assertEquals(1, (int) read(list, "$[0].playerCount"));
        assertEquals(4, (int) read(list, "$[0].maxPlayers"));
        assertEquals("ONLINE", read(list, "$[0].mode"));
        assertTrue(read(list, "$[0].createdAt") != null);
        assertEquals("🦊", read(list, "$[1].hostAvatarEmoji"));
        assertEquals(2, (int) read(list, "$[1].playerCount"));
        assertEquals("LIVE", read(list, "$[1].mode"));

        // privatne sobe se ne izlistavaju
        expectError(mvc.perform(get("/api/lobbies").param("public", "false")),
                HttpStatus.BAD_REQUEST, VALIDATION_ERROR);
    }

    @Test
    void publicLobbyListIsLimitedToTwentyNewest() throws Exception {
        OffsetDateTime base = OffsetDateTime.now();
        for (int i = 1; i <= 21; i++) {
            lobbyRepository.save(Lobby.builder().code("P" + i).status("WAITING").maxPlayers(4)
                    .isPublic(true).createdAt(base.plusSeconds(i)).build());
        }

        String list = ok(mvc.perform(get("/api/lobbies").param("public", "true")));

        List<String> codes = read(list, "$[*].code");
        assertEquals(20, codes.size());
        assertEquals("P21", codes.getFirst());
        assertEquals("P2", codes.getLast());
        // soba bez domaćina ne smije srušiti popis
        assertNull(read(list, "$[0].hostNickname"));
        assertEquals(0, (int) read(list, "$[0].playerCount"));
    }

    @Test
    void leavingWaitingLobbyFreesNicknameAndHandsOverHost() throws Exception {
        createLobby(Map.of("Mafia", 1, "Građanin", 3));
        Seat first = joinSeat("Prvi");
        Seat second = joinSeat("Drugi");

        // zadnji koji je ušao izlazi: nadimak se oslobađa
        ok(post(lobbyPath() + "/leave", auth(second)));
        assertEquals(2, ((List<?>) read(lobbyDetails(code), "$.players")).size());
        Seat again = joinSeat("Drugi");
        // igrač je obrisan, pa ni stari token ne vrijedi
        expectError(post(lobbyPath() + "/leave", auth(second)), HttpStatus.NOT_FOUND, PLAYER_NOT_FOUND);
        expectError(post(lobbyPath() + "/leave", Map.of("playerId", first.id(), "playerToken", "kriv")),
                HttpStatus.UNAUTHORIZED, INVALID_TOKEN);

        // domaćin izlazi: domaćin postaje onaj tko je najduže u sobi
        ok(post(lobbyPath() + "/leave", auth(host)));
        String details = lobbyDetails(code);
        assertEquals(first.id(), ((Number) read(details, "$.hostPlayerId")).longValue());
        assertEquals(Boolean.TRUE, playerField(details, first, "host"));
        assertEquals(Boolean.FALSE, playerField(details, again, "host"));

        // događaji
        assertEquals(List.of(second.id(), host.id()), events("PLAYER_LEFT").stream()
                .map(event -> ((PlayerLeftResponse) event.getData()).playerId()).toList());
        List<LobbyEventResponse> hostChanged = events("HOST_CHANGED");
        assertEquals(1, hostChanged.size());
        assertEquals(first.id(), ((HostChangedResponse) hostChanged.getFirst().getData()).hostPlayerId());

        // kad izađu svi, soba se briše
        ok(post(lobbyPath() + "/leave", auth(first)));
        ok(post(lobbyPath() + "/leave", auth(again)));
        expectError(mvc.perform(get(lobbyPath())), HttpStatus.NOT_FOUND, LOBBY_NOT_FOUND);
    }

    @Test
    void leavingWithAnotherLobbyCodeIsForbidden() throws Exception {
        createLobby(Map.of("Mafia", 1, "Građanin", 3));
        String other = create("Drugi", Map.of());
        expectError(post("/api/lobbies/" + read(other, "$.code") + "/leave", auth(host)),
                HttpStatus.FORBIDDEN, NOT_IN_LOBBY);
        expectError(post("/api/lobbies/NEMA00/leave", auth(host)), HttpStatus.NOT_FOUND, LOBBY_NOT_FOUND);
    }

    @Test
    void leavingDuringGameReturnsGameInProgress() throws Exception {
        createAndStart(Map.of("Mafia", 1, "Građanin", 3));
        Seat citizen = byRole.get("Građanin").getFirst();

        expectError(post(lobbyPath() + "/leave", auth(citizen)), HttpStatus.CONFLICT, GAME_IN_PROGRESS);
        expectError(post(lobbyPath() + "/leave", auth(host)), HttpStatus.CONFLICT, GAME_IN_PROGRESS);

        // igrač je i dalje u igri, s važećim tokenom
        String state = gameState(citizen);
        assertTrue(bool(state, "$.currentPlayer.alive"));
        assertEquals(4, ((List<?>) read(state, "$.players")).size());
        assertEquals(List.of(), events("PLAYER_LEFT"));
    }

    @Test
    void swaggerDocumentsNewEndpointsAndFields() throws Exception {
        String docs = ok(mvc.perform(get("/v3/api-docs")));
        assertTrue(docs.contains("\"/api/lobbies\""));
        assertTrue(docs.contains("\"/api/lobbies/{code}/leave\""));
        assertTrue(docs.contains("Popis javnih soba"));
        assertTrue(docs.contains("GAME_IN_PROGRESS"));
        assertTrue(docs.contains("hostAvatarEmoji"));
        assertTrue(docs.contains("voiceEnabled"));
        assertTrue(docs.contains("hostPlayerId"));
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

    private Map<String, Object> lobbyBody(String hostNickname, Map<String, Integer> composition,
                                          Map<String, Object> extra) {
        Map<String, Object> body = lobbyBody(hostNickname, composition);
        body.putAll(extra);
        return body;
    }

    /** Stvori još jednu sobu (4 igrača) s dodatnim poljima zahtjeva; vraća JSON odgovor */
    private String create(String hostNickname, Map<String, Object> extra) throws Exception {
        return ok(post("/api/lobbies", lobbyBody(hostNickname, Map.of("Mafia", 1, "Građanin", 3), extra)));
    }

    private String lobbyDetails(String lobbyCode) throws Exception {
        return ok(mvc.perform(get("/api/lobbies/" + lobbyCode)));
    }

    /** Domaćin iz odgovora na stvaranje sobe */
    private static Seat seatOf(String created, String idPath) {
        return new Seat(((Number) read(created, idPath)).longValue(), read(created, "$.playerToken"));
    }

    private Seat joinSeat(String nickname) throws Exception {
        String joined = ok(join(nickname));
        return new Seat(((Number) read(joined, "$.playerId")).longValue(), read(joined, "$.playerToken"));
    }

    /** Događaji tipa type koje je server poslao na temu sobe (tek nakon commita) */
    private List<LobbyEventResponse> events(String type) {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeast(0)).convertAndSend(eq("/topic/lobby/" + code), payloads.capture());
        return payloads.getAllValues().stream()
                .filter(LobbyEventResponse.class::isInstance)
                .map(LobbyEventResponse.class::cast)
                .filter(event -> type.equals(event.getType()))
                .toList();
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

    private Object playerFieldOfList(String players, Seat seat, String field) {
        List<Object> values = JsonPath.read(players, "$[?(@.id == " + seat.id() + ")]." + field);
        return values.getFirst();
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
