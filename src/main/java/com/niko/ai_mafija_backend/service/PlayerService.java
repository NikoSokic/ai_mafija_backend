package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Response.JoinLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerRoleResponse;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;

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
                .build();

        Player savedPlayer = playerRepository.save(player);

        return new JoinLobbyResponse(
                savedPlayer.getId(),
                savedPlayer.getNickname(),
                savedPlayer.getIsAlive(),
                lobby.getId(),
                lobby.getCode()
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

    public PlayerRoleResponse getPlayerRole(Long playerId) {

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Igrač ne postoji"));

        if (player.getRole() == null) {
            throw new RuntimeException("Uloga još nije dodijeljena");
        }

        return new PlayerRoleResponse(
                player.getId(),
                player.getNickname(),
                player.getRole().getName()
        );
    }
}