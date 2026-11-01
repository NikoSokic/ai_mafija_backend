package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Response.LobbyDetailsResponse;
import com.niko.ai_mafija_backend.dto.Response.PublicGameStateResponse;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.LobbyRole;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.service.LobbyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.niko.ai_mafija_backend.GameFlowIntegrationTests.assertCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Soba: napuštanje, domaćin, sastav uloga, kodovi grešaka */
class LobbyRulesTests extends GameTestSupport {

    @Autowired private LobbyService lobbyService;

    @Test
    void hostLeavingWaitingLobbyHandsHostToEarliestPlayer() {
        Lobby lobby = waitingLobby();
        Player host = host(lobby, "Mafia");
        Player next = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);

        playerService.leaveLobby(lobby.getCode(), leave(host));

        assertFalse(playerRepository.existsById(host.getId()));
        assertTrue(reload(next).getIsHost());
        assertEquals(next.getId(), lobbyRepository.findById(lobby.getId()).orElseThrow().getHostPlayer().getId());
    }

    @Test
    void lastPlayerLeavingDeletesLobby() {
        Lobby lobby = waitingLobby();
        Player host = host(lobby, "Mafia");

        playerService.leaveLobby(lobby.getCode(), leave(host));

        assertTrue(lobbyRepository.findByCode(lobby.getCode()).isEmpty());
    }

    @Test
    void leavingDuringGameEliminatesPlayerAsLeft() {
        Lobby lobby = lobby();
        host(lobby, "Mafia");
        player(lobby, "Mafia", true);
        Player leaver = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        state(lobby, "DAY", 1);

        playerService.leaveLobby(lobby.getCode(), leave(leaver));

        Player gone = reload(leaver);
        assertFalse(gone.getIsAlive());
        assertEquals("LEFT", gone.getEliminatedHow());
        assertEquals(1, gone.getEliminatedRound());
        assertNull(gone.getPlayerToken());
        assertEquals("IN_PROGRESS", lobbyRepository.findById(lobby.getId()).orElseThrow().getStatus());
        assertEquals("DAY", reload(lobby).getPhase());
    }

    @Test
    void lastMafiaLeavingDuringGameEndsItForCitizens() {
        Lobby lobby = lobby();
        host(lobby, "Građanin");
        Player mafia = player(lobby, "Mafia", true);
        player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        state(lobby, "DAY", 1);

        playerService.leaveLobby(lobby.getCode(), leave(mafia));

        assertEquals("FINISHED", lobbyRepository.findById(lobby.getId()).orElseThrow().getStatus());
        assertEquals("FINISHED", reload(lobby).getPhase());
    }

    @Test
    void leavingDuringVotingDoesNotBlockTheVote() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player a = player(lobby, "Građanin", true);
        Player b = player(lobby, "Građanin", true);
        Player leaver = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        state(lobby, "VOTING", 1);
        vote(host, null);
        vote(a, null);
        vote(b, null);
        // još glasa zadnji građanin; kad odlazi onaj koji je trebao glasati, glasanje se završava
        Player lastVoter = playerRepository.findByLobby(lobby).stream()
                .filter(p -> !List.of(host.getId(), a.getId(), b.getId(), leaver.getId()).contains(p.getId()))
                .findFirst().orElseThrow();
        vote(leaver, null);
        assertEquals("VOTING", reload(lobby).getPhase());

        playerService.leaveLobby(lobby.getCode(), leave(lastVoter));

        assertEquals("NIGHT", reload(lobby).getPhase());
    }

    @Test
    void lobbyDetailsShowRoleCompositionAndHost() {
        Lobby lobby = waitingLobby();
        Player host = host(lobby, "Mafia");
        player(lobby, "Građanin", true);
        lobbyRoleRepository.save(LobbyRole.builder().lobby(lobby).role(host.getRole()).roleCount(1).build());
        lobbyRoleRepository.save(LobbyRole.builder().lobby(lobby)
                .role(roleRepository.findAll().stream().filter(r -> r.getName().equals("Građanin")).findFirst().orElseThrow())
                .roleCount(3).build());

        LobbyDetailsResponse details = lobbyService.getLobbyDetails(lobby.getCode());

        assertEquals(2, details.getRoles().size());
        assertEquals(4, details.getRoles().stream().mapToInt(role -> role.count()).sum());
        assertTrue(details.getPlayers().stream().filter(p -> p.getId().equals(host.getId())).findFirst().orElseThrow().getHost());
    }

    @Test
    void errorsCarryStableCodes() {
        Lobby lobby = waitingLobby();
        Player host = host(lobby, "Mafia");

        JoinLobbyRequest join = new JoinLobbyRequest();
        join.setNickname(host.getNickname());
        assertCode(ErrorCode.NICKNAME_TAKEN, () -> playerService.joinLobby(lobby.getCode(), join));
        assertCode(ErrorCode.LOBBY_NOT_FOUND, () -> playerService.joinLobby("NEMA00", join));
    }

    private Lobby waitingLobby() {
        Lobby lobby = lobby();
        lobby.setStatus("WAITING");
        return lobbyRepository.save(lobby);
    }

    private PlayerAuthRequest leave(Player player) {
        return new PlayerAuthRequest(player.getId(), player.getPlayerToken());
    }
}
