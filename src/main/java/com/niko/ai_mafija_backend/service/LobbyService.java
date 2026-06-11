package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.CreateLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.LobbyRoleRequest;
import com.niko.ai_mafija_backend.dto.Response.CreateLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyDetailsResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.LobbyRole;
import com.niko.ai_mafija_backend.model.Role;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.LobbyRoleRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.niko.ai_mafija_backend.model.Player;
import jakarta.transaction.Transactional;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.repository.GameStateRepository;


import java.time.OffsetDateTime;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LobbyService {

    private final LobbyRepository lobbyRepository;
    private final RoleRepository roleRepository;
    private final LobbyRoleRepository lobbyRoleRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    public CreateLobbyResponse createLobby(CreateLobbyRequest request) {

        int totalRoles = request.getRoles()
                .stream()
                .mapToInt(LobbyRoleRequest::getCount)
                .sum();

        if (totalRoles != request.getMaxPlayers()) {
            throw new RuntimeException("Broj uloga mora biti jednak broju igrača.");
        }

        Lobby lobby = Lobby.builder()
                .code(generateLobbyCode())
                .status("WAITING")
                .maxPlayers(request.getMaxPlayers())
                .createdAt(OffsetDateTime.now())
                .build();

        Lobby savedLobby = lobbyRepository.save(lobby);

        Player hostPlayer = Player.builder()
                .nickname(request.getHostNickname())
                .lobby(savedLobby)
                .isHost(true)
                .isAlive(true)
                .joinedAt(OffsetDateTime.now())
                .build();

        Player savedHostPlayer = playerRepository.save(hostPlayer);

        savedLobby.setHostPlayer(savedHostPlayer);
        lobbyRepository.save(savedLobby);

        for (LobbyRoleRequest roleRequest : request.getRoles()) {

            Role role = roleRepository.findById(roleRequest.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Uloga ne postoji."));

            LobbyRole lobbyRole = LobbyRole.builder()
                    .lobby(savedLobby)
                    .role(role)
                    .roleCount(roleRequest.getCount())
                    .build();

            lobbyRoleRepository.save(lobbyRole);
        }

        return new CreateLobbyResponse(
                savedLobby.getId(),
                savedLobby.getCode(),
                savedLobby.getStatus(),
                savedLobby.getMaxPlayers(),
                savedHostPlayer.getId(),
                savedHostPlayer.getNickname()
        );
    }

    private String generateLobbyCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }

        return code.toString();
    }

    @Transactional
    public void startLobby(String code) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Lobby ne postoji"));

        if (!lobby.getStatus().equals("WAITING")) {
            throw new RuntimeException("Igra je već započela ili lobby nije u WAITING stanju");
        }

        List<Player> players = playerRepository.findByLobby(lobby);

        if (players.size() != lobby.getMaxPlayers()) {
            throw new RuntimeException("Lobby nije pun");
        }

        List<LobbyRole> lobbyRoles = lobbyRoleRepository.findByLobby(lobby);

        List<Role> rolesToAssign = new ArrayList<>();

        for (LobbyRole lobbyRole : lobbyRoles) {
            for (int i = 0; i < lobbyRole.getRoleCount(); i++) {
                rolesToAssign.add(lobbyRole.getRole());
            }
        }

        if (rolesToAssign.size() != players.size()) {
            throw new RuntimeException("Broj uloga ne odgovara broju igrača");
        }

        Collections.shuffle(players);
        Collections.shuffle(rolesToAssign);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            player.setRole(rolesToAssign.get(i));
            playerRepository.save(player);
        }

        lobby.setStatus("IN_PROGRESS");
        lobby.setStartedAt(OffsetDateTime.now());

        lobbyRepository.save(lobby);


        GameState gameState = new GameState();
        gameState.setLobby(lobby);
        gameState.setPhase("NIGHT");
        gameState.setRoundNumber(1);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameState.setCurrentTurnRole("MAFIA");

        gameStateRepository.save(gameState);
    }

    public LobbyDetailsResponse getLobbyDetails(String code) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Lobby ne postoji"));

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElse(null);

        List<Player> players = playerRepository.findByLobby(lobby);

        List<LobbyPlayerResponse> playerResponses = players.stream()
                .map(player -> new LobbyPlayerResponse(
                        player.getId(),
                        player.getNickname(),
                        player.getIsAlive()
                ))
                .toList();

        return new LobbyDetailsResponse(
                lobby.getId(),
                lobby.getCode(),
                lobby.getStatus(),
                lobby.getMaxPlayers(),
                gameState != null ? gameState.getPhase() : null,
                gameState != null ? gameState.getRoundNumber() : null,
                playerResponses
        );
    }
}