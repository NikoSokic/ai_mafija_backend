package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.CreateLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.LobbyRoleRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Response.CreateLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyDetailsResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
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
import java.util.*;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class LobbyService {

    private static final String LOBBY_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LOBBY_CODE_LENGTH = 6;
    private static final int MAX_LOBBY_CODE_ATTEMPTS = 10;
    private final SecureRandom secureRandom = new SecureRandom();

    private final LobbyRepository lobbyRepository;
    private final RoleRepository roleRepository;
    private final LobbyRoleRepository lobbyRoleRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final WebSocketService webSocketService;
    private final PlayerAuthService playerAuthService;
    private final ActionService actionService;

    @Transactional
    public CreateLobbyResponse createLobby(CreateLobbyRequest request) {

        validateRoleConfiguration(request);

        int totalRoles = request.getRoles()
                .stream()
                .mapToInt(LobbyRoleRequest::getCount)
                .sum();

        if (totalRoles != request.getMaxPlayers()) {
            throw new BadRequestException("Broj uloga mora biti jednak broju igrača.");
        }

        Lobby lobby = Lobby.builder()
                .code(generateUniqueLobbyCode())
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
                .playerToken(UUID.randomUUID().toString())
                .build();

        Player savedHostPlayer = playerRepository.save(hostPlayer);

        savedLobby.setHostPlayer(savedHostPlayer);
        lobbyRepository.save(savedLobby);

        for (LobbyRoleRequest roleRequest : request.getRoles()) {

            Role role = roleRepository.findById(roleRequest.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Uloga ne postoji."));

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
                savedHostPlayer.getNickname(),
                savedHostPlayer.getPlayerToken()
        );
    }

    private void validateRoleConfiguration(CreateLobbyRequest request) {
        Set<Long> roleIds = new HashSet<>();

        for (LobbyRoleRequest roleRequest : request.getRoles()) {
            if (!roleIds.add(roleRequest.getRoleId())) {
                throw new BadRequestException("Ista uloga ne može biti navedena više puta.");
            }

            if (!roleRepository.existsById(roleRequest.getRoleId())) {
                throw new ResourceNotFoundException("Uloga ne postoji.");
            }
        }
    }

    private String generateUniqueLobbyCode() {
        for (int attempt = 0; attempt < MAX_LOBBY_CODE_ATTEMPTS; attempt++) {
            String code = generateLobbyCode();
            if (lobbyRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }

        throw new ConflictException("Nije moguće generirati jedinstveni kod lobija. Pokušaj ponovno.");
    }

    private String generateLobbyCode() {
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < LOBBY_CODE_LENGTH; i++) {
            code.append(LOBBY_CODE_CHARACTERS.charAt(
                    secureRandom.nextInt(LOBBY_CODE_CHARACTERS.length())
            ));
        }

        return code.toString();
    }

    @Transactional
    public void startLobby(String code, HostActionRequest request) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);

        if (!lobby.getStatus().equals("WAITING")) {
            throw new ConflictException("Igra je već započela ili lobby nije u WAITING stanju.");
        }

        List<Player> players = playerRepository.findByLobby(lobby);

        if (players.size() != lobby.getMaxPlayers()) {
            throw new ConflictException("Lobby nije pun.");
        }

        List<LobbyRole> lobbyRoles = lobbyRoleRepository.findByLobby(lobby);
        validateStartedLobbyRoleConfiguration(lobbyRoles);

        List<Role> rolesToAssign = new ArrayList<>();

        for (LobbyRole lobbyRole : lobbyRoles) {
            for (int i = 0; i < lobbyRole.getRoleCount(); i++) {
                rolesToAssign.add(lobbyRole.getRole());
            }
        }

        if (rolesToAssign.size() != players.size()) {
            throw new ConflictException("Broj uloga ne odgovara broju igrača.");
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

        webSocketService.publishLobbyEventAfterCommit(
                lobby.getCode(),
                "GAME_STARTED",
                "Igra je započela.",
                null
        );

        GameState gameState = new GameState();
        gameState.setLobby(lobby);
        gameState.setPhase("NIGHT");
        gameState.setRoundNumber(1);
        gameState.setUpdateAt(OffsetDateTime.now());
        gameState.setCurrentTurnRole("MAFIA");

        gameStateRepository.save(gameState);
        actionService.initializeNightTurn(lobby, gameState);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    private void validateStartedLobbyRoleConfiguration(List<LobbyRole> lobbyRoles) {
        Set<Long> roleIds = new HashSet<>();

        for (LobbyRole lobbyRole : lobbyRoles) {
            if (lobbyRole.getRole() == null
                    || lobbyRole.getRole().getId() == null
                    || lobbyRole.getRoleCount() == null
                    || lobbyRole.getRoleCount() <= 0) {
                throw new ConflictException("Konfiguracija uloga lobija nije valjana.");
            }

            if (!roleIds.add(lobbyRole.getRole().getId())) {
                throw new ConflictException("Konfiguracija uloga sadrži duplikat uloge.");
            }
        }
    }

    public LobbyDetailsResponse getLobbyDetails(String code) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Lobby ne postoji."));

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
