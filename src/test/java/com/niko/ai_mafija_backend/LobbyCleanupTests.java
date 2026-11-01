package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.LobbyRole;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.ReclaimRequest;
import com.niko.ai_mafija_backend.model.Vote;
import com.niko.ai_mafija_backend.service.LobbyCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Brisanje napuštenih soba. "Sada" je 3 sata unaprijed, da prođe i zaštita nakon pokretanja servera. */
class LobbyCleanupTests extends GameTestSupport {

    private static final OffsetDateTime NOW = OffsetDateTime.now().plusHours(3);

    @Autowired private LobbyCleanupService cleanupService;

    @Test
    void removesAbandonedGameWithAllItsData() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        Player other = player(lobby, "Građanin", true);
        state(lobby, "VOTING", 1);
        lobbyRoleRepository.save(LobbyRole.builder().lobby(lobby).role(host.getRole()).roleCount(1).build());
        voteRepository.save(Vote.builder().lobby(lobby).voterPlayer(host).targetPlayer(other)
                .roundNumber(1).phase("VOTING").createdAt(NOW).build());
        gameActionRepository.save(GameAction.builder().lobby(lobby).player(host).targetPlayer(other)
                .actionType("KILL").phase("NIGHT").roundNumber(1).createdAt(NOW).build());
        reclaimRequestRepository.save(ReclaimRequest.builder().lobbyId(lobby.getId()).playerId(other.getId())
                .claimSecret("tajna").status(ReclaimRequest.PENDING).createdAt(NOW).expiresAt(NOW).build());

        cleanupService.removeAbandonedLobbies(NOW);

        assertThat(lobbyRepository.findById(lobby.getId())).isEmpty();
        assertThat(playerRepository.count()).isZero();
        assertThat(gameStateRepository.count()).isZero();
        assertThat(voteRepository.count()).isZero();
        assertThat(gameActionRepository.count()).isZero();
        assertThat(lobbyRoleRepository.count()).isZero();
        assertThat(reclaimRequestRepository.count()).isZero();
    }

    @Test
    void removesAbandonedWaitingAndFinishedLobbies() {
        Lobby waiting = lobby();
        waiting.setStatus("WAITING");
        lobbyRepository.save(waiting);
        host(waiting, "Mafia");
        Lobby finished = lobby();
        finished.setStatus("FINISHED");
        lobbyRepository.save(finished);
        host(finished, "Mafia");
        state(finished, "FINISHED", 2);

        cleanupService.removeAbandonedLobbies(NOW);

        assertThat(lobbyRepository.count()).isZero();
    }

    @Test
    void oneActivePlayerKeepsTheLobby() {
        Lobby lobby = lobby();
        host(lobby, "Mafia");
        Player active = player(lobby, "Građanin", true);
        active.setLastSeenAt(NOW.minusMinutes(59));
        playerRepository.save(active);
        state(lobby, "DAY", 1);

        cleanupService.removeAbandonedLobbies(NOW);

        assertThat(lobbyRepository.findById(lobby.getId())).isPresent();
        assertThat(playerRepository.count()).isEqualTo(2);
    }

    @Test
    void lobbyIsRemovedAfterLastPlayerGoesQuietForAnHour() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        host.setLastSeenAt(NOW.minusMinutes(61));
        playerRepository.save(host);

        cleanupService.removeAbandonedLobbies(NOW);

        assertThat(lobbyRepository.findById(lobby.getId())).isEmpty();
    }

    @Test
    void nothingIsRemovedRightAfterServerStart() {
        Lobby lobby = lobby();
        Player host = host(lobby, "Mafia");
        host.setLastSeenAt(OffsetDateTime.now().minusHours(5));
        host.setJoinedAt(OffsetDateTime.now().minusHours(5));
        playerRepository.save(host);

        // server radi kraće od sat vremena, pa se tih 5 sati može računati kao prekid rada
        cleanupService.removeAbandonedLobbies(OffsetDateTime.now());

        assertThat(lobbyRepository.findById(lobby.getId())).isPresent();
    }
}
