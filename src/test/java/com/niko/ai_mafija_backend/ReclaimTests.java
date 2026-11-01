package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReclaimDecisionRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReclaimLobbyRequest;
import com.niko.ai_mafija_backend.dto.Response.ReclaimCreatedResponse;
import com.niko.ai_mafija_backend.dto.Response.ReclaimStatusResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.ReclaimRequest;
import com.niko.ai_mafija_backend.service.HostPresenceService;
import com.niko.ai_mafija_backend.service.ReclaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Povratak na novom uređaju: zahtjev, odluka domaćina, preuzimanje novog tokena */
class ReclaimTests extends GameTestSupport {

    @Autowired private ReclaimService reclaimService;
    @Autowired private HostPresenceService hostPresenceService;

    @Test
    void approvedRequestGivesNewTokenOnceAndInvalidatesTheOldOne() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player lost = player(lobby, "Građanin", true);
        String oldToken = lost.getPlayerToken();

        ReclaimCreatedResponse created = reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname().toUpperCase()));
        assertEquals("PENDING", reclaimService.status(lobby.getCode(), created.requestId(), created.claimSecret()).status());

        reclaimService.decide(lobby.getCode(), created.requestId(), decision(host, true));
        // stari token vrijedi dok novi uređaj ne preuzme mjesto
        assertEquals(oldToken, reload(lost).getPlayerToken());

        ReclaimStatusResponse claimed = reclaimService.status(lobby.getCode(), created.requestId(), created.claimSecret());
        assertEquals("APPROVED", claimed.status());
        assertEquals(lost.getId(), claimed.playerId());
        assertNotEquals(oldToken, claimed.playerToken());
        assertEquals(claimed.playerToken(), reload(lost).getPlayerToken());

        // novi token se vraća samo jednom
        ReclaimStatusResponse again = reclaimService.status(lobby.getCode(), created.requestId(), created.claimSecret());
        assertEquals("CLAIMED", again.status());
        assertNull(again.playerToken());
    }

    @Test
    void deniedRequestKeepsTheOldToken() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player lost = player(lobby, "Građanin", true);
        ReclaimCreatedResponse created = reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname()));

        reclaimService.decide(lobby.getCode(), created.requestId(), decision(host, false));

        ReclaimStatusResponse status = reclaimService.status(lobby.getCode(), created.requestId(), created.claimSecret());
        assertEquals("DENIED", status.status());
        assertNull(status.playerToken());
        assertEquals(lost.getPlayerToken(), reload(lost).getPlayerToken());
    }

    @Test
    void onlyTheHostDecidesAndOnlyOnePendingRequestExistsPerPlayer() {
        Lobby lobby = lobby();
        host(lobby, "Mafia");
        Player lost = player(lobby, "Građanin", true);
        Player other = player(lobby, "Građanin", true);

        ReclaimCreatedResponse first = reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname()));
        assertCode(ErrorCode.NOT_HOST, () -> reclaimService.decide(lobby.getCode(), first.requestId(), decision(other, true)));

        ReclaimCreatedResponse second = reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname()));
        assertEquals("EXPIRED", reclaimService.status(lobby.getCode(), first.requestId(), first.claimSecret()).status());
        assertEquals("PENDING", reclaimService.status(lobby.getCode(), second.requestId(), second.claimSecret()).status());
    }

    @Test
    void wrongSecretUnknownNicknameAndPlayerWhoLeftAreRejected() {
        Lobby lobby = lobby();
        host(lobby, "Mafia");
        Player lost = player(lobby, "Građanin", true);
        Player gone = player(lobby, "Građanin", true);
        gone.setPlayerToken(null);
        playerRepository.save(gone);

        ReclaimCreatedResponse created = reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname()));

        assertCode(ErrorCode.RECLAIM_NOT_FOUND, () -> reclaimService.status(lobby.getCode(), created.requestId(), "kriva"));
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest("Nitko")));
        assertCode(ErrorCode.RECLAIM_NOT_ALLOWED,
                () -> reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(gone.getNickname())));
    }

    @Test
    void expiredRequestCannotBeDecidedOrClaimed() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player lost = player(lobby, "Građanin", true);
        ReclaimCreatedResponse created = reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname()));
        ReclaimRequest stored = reclaimRequestRepository.findById(created.requestId()).orElseThrow();
        stored.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        reclaimRequestRepository.save(stored);

        assertCode(ErrorCode.RECLAIM_NOT_FOUND,
                () -> reclaimService.decide(lobby.getCode(), created.requestId(), decision(host, true)));
        assertEquals("EXPIRED", reclaimService.status(lobby.getCode(), created.requestId(), created.claimSecret()).status());
        assertEquals(lost.getPlayerToken(), reload(lost).getPlayerToken());
    }

    @Test
    void heartbeatShowsPendingRequestsToTheHostOnly() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player lost = player(lobby, "Građanin", true);
        reclaimService.request(lobby.getCode(), new ReclaimLobbyRequest(lost.getNickname()));

        var forHost = hostPresenceService.heartbeat(new PlayerAuthRequest(host.getId(), host.getPlayerToken()));
        assertEquals(1, forHost.reclaimRequests().size());
        assertEquals(lost.getNickname(), forHost.reclaimRequests().getFirst().nickname());

        Player other = player(lobby, "Građanin", true);
        var forOther = hostPresenceService.heartbeat(new PlayerAuthRequest(other.getId(), other.getPlayerToken()));
        assertTrue(forOther.reclaimRequests().isEmpty());
    }

    private ReclaimDecisionRequest decision(Player player, boolean approve) {
        return new ReclaimDecisionRequest(player.getId(), player.getPlayerToken(), approve);
    }
}
