package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.KickPlayerRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Response.HeartbeatResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.service.HostPresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Izbacivanje iz čekaonice, "Nije tu" i prijenos domaćina koji se ne javlja */
class HostPresenceTests extends GameTestSupport {

    @Autowired private HostPresenceService hostPresenceService;

    private final OffsetDateTime t0 = OffsetDateTime.now();

    // ---------- izbacivanje (task 13) ----------

    @Test
    void hostKicksPlayerFromWaitingLobbyAndNicknameIsFreed() {
        Lobby lobby = waitingLobby();
        Player host = host(lobby, "Mafia");
        Player gone = player(lobby, "Građanin", true);

        playerService.kickPlayer(lobby.getCode(), kick(host, gone));

        assertFalse(playerRepository.existsById(gone.getId()));
        JoinLobbyRequest rejoin = new JoinLobbyRequest();
        rejoin.setNickname(gone.getNickname());
        assertNotNull(playerService.joinLobby(lobby.getCode(), rejoin).getPlayerToken());
    }

    @Test
    void onlyHostCanKick() {
        Lobby lobby = waitingLobby();
        host(lobby, "Mafia");
        Player other = player(lobby, "Građanin", true);
        Player target = player(lobby, "Građanin", true);

        assertCode(ErrorCode.NOT_HOST, () -> playerService.kickPlayer(lobby.getCode(), kick(other, target)));
        assertTrue(playerRepository.existsById(target.getId()));
    }

    @Test
    void hostCannotKickSelfOrPlayerFromAnotherLobby() {
        Lobby lobby = waitingLobby();
        Player host = host(lobby, "Mafia");
        Player stranger = player(waitingLobby(), "Građanin", true);

        assertCode(ErrorCode.CANNOT_KICK_SELF, () -> playerService.kickPlayer(lobby.getCode(), kick(host, host)));
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> playerService.kickPlayer(lobby.getCode(), kick(host, stranger)));
        assertTrue(playerRepository.existsById(stranger.getId()));
    }

    @Test
    void cannotKickOnceGameHasStarted() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player target = player(lobby, "Građanin", true);
        state(lobby, "DAY", 1);

        assertCode(ErrorCode.WRONG_PHASE, () -> playerService.kickPlayer(lobby.getCode(), kick(host, target)));
        assertTrue(playerRepository.existsById(target.getId()));
    }

    // ---------- prijenos domaćina (taskovi 14 i 15) ----------

    @Test
    void waitingLobbyHostKeepsRoleBeforeTwoMinutes() {
        Lobby lobby = waitingLobby();
        Player host = hostSeen(lobby, t0);
        Player next = playerSeen(lobby, t0.plusSeconds(100));

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(110));

        assertTrue(reload(host).getIsHost());
        assertFalse(reload(next).getIsHost());
    }

    @Test
    void waitingLobbyHostLosesRoleAfterTwoMinutesButStaysInRoom() {
        Lobby lobby = waitingLobby();
        Player host = hostSeen(lobby, t0);
        Player next = playerSeen(lobby, t0.plusSeconds(130));

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(131));

        assertFalse(reload(host).getIsHost());
        assertTrue(playerRepository.existsById(host.getId()));
        assertTrue(reload(next).getIsHost());
        assertEquals(next.getId(), lobbyRepository.findById(lobby.getId()).orElseThrow().getHostPlayer().getId());
    }

    @Test
    void hostInGameLosesRoleAfter45Seconds() {
        Lobby lobby = lobby();
        Player host = hostSeen(lobby, t0);
        Player next = playerSeen(lobby, t0.plusSeconds(50));
        state(lobby, "NIGHT", 1);

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(40));
        assertTrue(reload(host).getIsHost());

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(51));
        assertFalse(reload(host).getIsHost());
        assertTrue(reload(next).getIsHost());
    }

    @Test
    void finishedGameUsesTheLongLimit() {
        Lobby lobby = lobby();
        lobby.setStatus("FINISHED");
        lobbyRepository.save(lobby);
        Player host = hostSeen(lobby, t0);
        Player next = playerSeen(lobby, t0.plusSeconds(100));

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(60));
        assertTrue(reload(host).getIsHost());

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(121));
        assertTrue(reload(next).getIsHost());
    }

    @Test
    void nothingChangesWhenNobodyElseIsPresent() {
        Lobby lobby = waitingLobby();
        Player host = hostSeen(lobby, t0);
        Player away = playerSeen(lobby, t0);

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(300));

        assertTrue(reload(host).getIsHost());
        assertFalse(reload(away).getIsHost());
    }

    @Test
    void preferAliveAndEarlierPlayersAndIgnoreThoseWhoLeft() {
        Lobby lobby = lobby();
        Player host = hostSeen(lobby, t0);
        Player dead = playerSeen(lobby, t0.plusSeconds(60));
        dead.setIsAlive(false);
        playerRepository.save(dead);
        Player left = playerSeen(lobby, t0.plusSeconds(60));
        left.setPlayerToken(null);
        playerRepository.save(left);
        Player alive = playerSeen(lobby, t0.plusSeconds(60));
        state(lobby, "DAY", 1);

        hostPresenceService.transferAbsentHosts(t0.plusSeconds(61));

        assertFalse(reload(host).getIsHost());
        assertTrue(reload(alive).getIsHost());
        assertFalse(reload(dead).getIsHost());
        assertFalse(reload(left).getIsHost());
    }

    @Test
    void serverRestartDoesNotCostTheHostHisRole() {
        // zadnje javljanje prije pokretanja servera: računa se od pokretanja, a ne od starog vremena
        Lobby lobby = waitingLobby();
        Player host = hostSeen(lobby, t0.minusSeconds(3000));
        playerSeen(lobby, t0.minusSeconds(3000));

        hostPresenceService.transferAbsentHosts(OffsetDateTime.now());

        assertTrue(reload(host).getIsHost());
    }

    // ---------- javljanje ----------

    @Test
    void heartbeatRecordsPresenceAndReportsHost() {
        Lobby lobby = waitingLobby();
        Player host = hostSeen(lobby, t0.minusSeconds(3000));
        Player other = playerSeen(lobby, t0.minusSeconds(3000));

        HeartbeatResponse response = hostPresenceService.heartbeat(new PlayerAuthRequest(other.getId(), other.getPlayerToken()));

        assertEquals(host.getId(), response.hostPlayerId());
        assertTrue(reload(other).getLastSeenAt().isAfter(t0.minusSeconds(5)));
        assertFalse(response.awayPlayerIds().contains(other.getId()));
    }

    @Test
    void awayListHoldsOnlyPlayersWhoStoppedReporting() {
        Lobby lobby = waitingLobby();
        hostSeen(lobby, t0.plusSeconds(100));
        Player away = playerSeen(lobby, t0);
        Player here = playerSeen(lobby, t0.plusSeconds(100));
        Player gone = playerSeen(lobby, t0);
        gone.setPlayerToken(null);
        playerRepository.save(gone);

        var ids = hostPresenceService.awayPlayerIds(lobby, t0.plusSeconds(110));

        assertEquals(java.util.List.of(away.getId()), ids);
        assertFalse(ids.contains(here.getId()));
    }

    // ---------- pomoćno ----------

    private Lobby waitingLobby() {
        Lobby lobby = lobby();
        lobby.setStatus("WAITING");
        return lobbyRepository.save(lobby);
    }

    private Player hostSeen(Lobby lobby, OffsetDateTime seen) {
        Player host = host(lobby, "Mafia");
        host.setLastSeenAt(seen);
        return playerRepository.save(host);
    }

    private Player playerSeen(Lobby lobby, OffsetDateTime seen) {
        Player player = player(lobby, "Građanin", true);
        player.setLastSeenAt(seen);
        return playerRepository.save(player);
    }

    private KickPlayerRequest kick(Player host, Player target) {
        return new KickPlayerRequest(host.getId(), host.getPlayerToken(), target.getId());
    }
}
