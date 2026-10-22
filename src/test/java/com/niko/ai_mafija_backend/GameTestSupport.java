package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Requests.VoteRequest;
import com.niko.ai_mafija_backend.dto.Response.VoteResponse;
import com.niko.ai_mafija_backend.model.GameAction;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.Role;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.LobbyRoleRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.RoleRepository;
import com.niko.ai_mafija_backend.repository.VoteRepository;
import com.niko.ai_mafija_backend.service.ActionService;
import com.niko.ai_mafija_backend.service.GamePhaseService;
import com.niko.ai_mafija_backend.service.GameStateService;
import com.niko.ai_mafija_backend.service.PlayerService;
import com.niko.ai_mafija_backend.service.VoteService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Zajedničko za testove pravila: H2 baza, timer isključen (app.timers.enabled=false),
 * a istek roka se glumi s expire() + GamePhaseService.handleTimeout.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class GameTestSupport {

    @Autowired protected ActionService actionService;
    @Autowired protected VoteService voteService;
    @Autowired protected PlayerService playerService;
    @Autowired protected GameStateService gameStateService;
    @Autowired protected GamePhaseService gamePhaseService;
    @Autowired protected LobbyRepository lobbyRepository;
    @Autowired protected LobbyRoleRepository lobbyRoleRepository;
    @Autowired protected PlayerRepository playerRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected GameStateRepository gameStateRepository;
    @Autowired protected GameActionRepository gameActionRepository;
    @Autowired protected VoteRepository voteRepository;

    @AfterEach
    void cleanDatabase() {
        gameActionRepository.deleteAll();
        voteRepository.deleteAll();
        gameStateRepository.deleteAll();
        List<Lobby> lobbies = lobbyRepository.findAll();
        lobbies.forEach(lobby -> lobby.setHostPlayer(null));
        lobbyRepository.saveAll(lobbies);
        lobbyRoleRepository.deleteAll();
        playerRepository.deleteAll();
        lobbyRepository.deleteAll();
        roleRepository.deleteAll();
    }

    protected Lobby lobby() {
        return lobbyRepository.save(Lobby.builder()
                .code("T" + System.nanoTime()).status("IN_PROGRESS").maxPlayers(20)
                .createdAt(OffsetDateTime.now()).build());
    }

    protected Player player(Lobby lobby, String roleName, boolean alive) {
        Role role = roleRepository.findAll().stream()
                .filter(existing -> existing.getName().equals(roleName))
                .findFirst()
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).opis(roleName).build()));
        return playerRepository.save(Player.builder()
                .lobby(lobby).role(role).nickname(roleName + System.nanoTime())
                .isHost(false).isAlive(alive).joinedAt(OffsetDateTime.now())
                .playerToken("token-" + System.nanoTime()).build());
    }

    protected Player host(Lobby lobby, String roleName) {
        Player host = player(lobby, roleName, true);
        host.setIsHost(true);
        lobby.setHostPlayer(host);
        lobbyRepository.save(lobby);
        return playerRepository.save(host);
    }

    protected GameState state(Lobby lobby, String phase, int round) {
        return gameStateRepository.save(GameState.builder().lobby(lobby).phase(phase)
                .roundNumber(round).updateAt(OffsetDateTime.now())
                .phaseEndsAt(OffsetDateTime.now().plusMinutes(5)).build());
    }

    protected GameState reload(Lobby lobby) {
        return gameStateRepository.findByLobby(lobby).orElseThrow();
    }

    protected Player reload(Player player) {
        return playerRepository.findById(player.getId()).orElseThrow();
    }

    /** Glumi istek roka trenutne faze ili poteza */
    protected void expire(Lobby lobby) {
        GameState state = reload(lobby);
        state.setPhaseEndsAt(OffsetDateTime.now().minusSeconds(1));
        gameStateRepository.save(state);
        gamePhaseService.handleTimeout(state.getId());
    }

    protected VoteResponse vote(Player voter, Player target) {
        VoteRequest request = new VoteRequest();
        request.setVoterPlayerId(voter.getId());
        request.setTargetPlayerId(target != null ? target.getId() : null);
        request.setPlayerToken(voter.getPlayerToken());
        return voteService.submitVote(request);
    }

    protected HostActionRequest auth(Player player) {
        return new HostActionRequest(player.getId(), player.getPlayerToken());
    }

    protected void silence(Lobby lobby, Player dama, Player target, int round) {
        gameActionRepository.save(GameAction.builder()
                .lobby(lobby).player(dama).targetPlayer(target)
                .actionType("SILENCE").phase("NIGHT").roundNumber(round)
                .createdAt(OffsetDateTime.now()).build());
    }
}
