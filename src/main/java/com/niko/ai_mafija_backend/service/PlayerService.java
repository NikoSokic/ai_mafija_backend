package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerRoleRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReconnectRequest;
import com.niko.ai_mafija_backend.dto.Response.JoinLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerRoleResponse;
import com.niko.ai_mafija_backend.dto.Response.ReconnectResponse;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAuthService playerAuthService;
    private final GameStateRepository gameStateRepository;
    private final WebSocketService webSocketService;

    public JoinLobbyResponse joinLobby(String code, JoinLobbyRequest request) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Lobby ne postoji."));

        if (!lobby.getStatus().equals("WAITING")) {
            throw new RuntimeException("Igra je već započela ili završena.");
        }

        if (playerRepository.existsByLobbyAndNicknameIgnoreCase(lobby, request.getNickname())) {
            throw new RuntimeException("Igrač s tim nicknameom već postoji u lobbyju.");
        }

        long currentPlayers = playerRepository.countByLobby(lobby);

        if (currentPlayers >= lobby.getMaxPlayers()) {
            throw new RuntimeException("Lobby je popunjen.");
        }

        Player player = Player.builder()
                .lobby(lobby)
                .nickname(request.getNickname())
                .isHost(false)
                .isAlive(true)
                .joinedAt(OffsetDateTime.now())
                .playerToken(UUID.randomUUID().toString())
                .build();

        Player savedPlayer = playerRepository.save(player);

        System.out.println("SALJEM WS PLAYER_JOINED ZA LOBBY: " + lobby.getCode());

        webSocketService.sendLobbyEvent(
                lobby.getCode(),
                "PLAYER_JOINED",
                "Novi igrač se pridružio lobbyju.",
                new LobbyPlayerResponse(
                        savedPlayer.getId(),
                        savedPlayer.getNickname(),
                        savedPlayer.getIsAlive()
                )
        );

        return new JoinLobbyResponse(
                savedPlayer.getId(),
                savedPlayer.getNickname(),
                savedPlayer.getIsAlive(),
                lobby.getId(),
                lobby.getCode(),
                savedPlayer.getPlayerToken()
        );
    }

    public List<LobbyPlayerResponse> getPlayersByLobby(String code) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Lobby ne postoji."));

        List<Player> players = playerRepository.findByLobby(lobby);

        return players.stream()
                .map(player -> new LobbyPlayerResponse(
                        player.getId(),
                        player.getNickname(),
                        player.getIsAlive()
                ))
                .toList();
    }

    public PlayerRoleResponse getPlayerRole(PlayerRoleRequest request) {

        Player player = playerAuthService.authenticate(
                request.getPlayerId(),
                request.getPlayerToken()
        );

        if (player.getRole() == null) {
            throw new RuntimeException("Uloga još nije dodijeljena");
        }

        return new PlayerRoleResponse(
                player.getId(),
                player.getNickname(),
                player.getRole().getName()
        );
    }

    public ReconnectResponse reconnect(ReconnectRequest request) {

        Player player = playerAuthService.authenticate(
                request.getPlayerId(),
                request.getPlayerToken()
        );

        Lobby lobby = player.getLobby();

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElse(null);

        String roleName = player.getRole() != null
                ? player.getRole().getName()
                : null;

        String phase = gameState != null
                ? gameState.getPhase()
                : null;

        Integer roundNumber = gameState != null
                ? gameState.getRoundNumber()
                : null;

        String currentTurnRole = gameState != null
                ? gameState.getCurrentTurnRole()
                : null;

        return new ReconnectResponse(
                true,
                player.getId(),
                player.getNickname(),
                player.getIsAlive(),
                player.getIsHost(),
                lobby.getId(),
                lobby.getCode(),
                lobby.getStatus(),
                roleName,
                phase,
                roundNumber,
                currentTurnRole
        );
    }

}