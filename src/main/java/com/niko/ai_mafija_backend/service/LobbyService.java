package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.CreateLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.LobbyRoleRequest;
import com.niko.ai_mafija_backend.dto.Requests.HostActionRequest;
import com.niko.ai_mafija_backend.dto.Response.CreateLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyDetailsResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyRoleResponse;
import com.niko.ai_mafija_backend.dto.Response.PublicLobbyResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.LobbyRole;
import com.niko.ai_mafija_backend.model.Role;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.VoteRepository;
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
    private final GameActionRepository gameActionRepository;
    private final VoteRepository voteRepository;
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
            throw new BadRequestException(ErrorCode.INVALID_ROLE_CONFIG, "Broj uloga mora biti jednak broju igrača.");
        }

        Lobby lobby = Lobby.builder()
                .code(generateUniqueLobbyCode())
                .status(GameRules.LOBBY_WAITING)
                .maxPlayers(request.getMaxPlayers())
                .mode(request.getMode() != null ? request.getMode() : GameRules.MODE_LIVE)
                .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
                .voiceEnabled(request.getVoiceEnabled() == null || request.getVoiceEnabled())
                .createdAt(OffsetDateTime.now())
                .build();

        Lobby savedLobby = lobbyRepository.save(lobby);

        Player hostPlayer = Player.builder()
                .nickname(request.getHostNickname().trim())
                .lobby(savedLobby)
                .isHost(true)
                .isAlive(true)
                .joinedAt(OffsetDateTime.now())
                .avatarEmoji(cleanAvatar(request.getHostAvatarEmoji()))
                .playerToken(UUID.randomUUID().toString())
                .build();

        Player savedHostPlayer = playerRepository.save(hostPlayer);

        savedLobby.setHostPlayer(savedHostPlayer);
        lobbyRepository.save(savedLobby);

        for (LobbyRoleRequest roleRequest : request.getRoles()) {

            Role role = roleRepository.findById(roleRequest.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Uloga ne postoji."));

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

    /** Prazan avatar se sprema kao null; razmaci oko emojija se odbacuju */
    public static String cleanAvatar(String avatarEmoji) {
        return avatarEmoji == null || avatarEmoji.isBlank() ? null : avatarEmoji.trim();
    }

    private void validateRoleConfiguration(CreateLobbyRequest request) {
        Set<Long> roleIds = new HashSet<>();

        for (LobbyRoleRequest roleRequest : request.getRoles()) {
            if (!roleIds.add(roleRequest.getRoleId())) {
                throw new BadRequestException(ErrorCode.INVALID_ROLE_CONFIG, "Ista uloga ne može biti navedena više puta.");
            }

            if (!roleRepository.existsById(roleRequest.getRoleId())) {
                throw new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Uloga ne postoji.");
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
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);

        if (!lobby.getStatus().equals(GameRules.LOBBY_WAITING)) {
            throw new ConflictException(ErrorCode.GAME_ALREADY_STARTED,
                    "Igra je već započela ili lobby nije u WAITING stanju.");
        }

        List<Player> players = playerRepository.findByLobby(lobby);

        if (players.size() != lobby.getMaxPlayers()) {
            throw new ConflictException(ErrorCode.LOBBY_NOT_FULL, "Lobby nije pun.");
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
            throw new ConflictException(ErrorCode.INVALID_ROLE_CONFIG, "Broj uloga ne odgovara broju igrača.");
        }

        Collections.shuffle(players);
        Collections.shuffle(rolesToAssign);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            player.setRole(rolesToAssign.get(i));
            player.setIsReady(false);
            playerRepository.save(player);
        }

        lobby.setStatus(GameRules.LOBBY_IN_PROGRESS);
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
        // prva noć počinje uvodnom pauzom dok igrači gledaju svoje uloge
        actionService.startNight(lobby, gameState, 1);
    }

    /**
     * "Igraj ponovno": domaćin vraća završenu igru u čekaonicu iste sobe. Brišu se akcije, glasovi i stanje igre
     * (pa timer stariju igru više ne može dirati); igrači koji su ostali dobivaju čisto stanje, a oni koji su izašli
     * se brišu. Sastav uloga i postavke sobe ostaju; nova igra kreće običnim START-om kad je soba puna.
     */
    @Transactional
    public void rematch(String code, HostActionRequest request) {
        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);

        // zaključavanje: dva istodobna poziva (ili timer) se ne preklapaju; drugi više ne nađe stanje igre
        GameState gameState = gameStateRepository.findByLobbyForUpdate(lobby).orElse(null);
        if (gameState == null || !GameRules.LOBBY_FINISHED.equals(lobby.getStatus())) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Nova igra se može pokrenuti samo kad je igra završena.");
        }

        voteRepository.deleteByLobby(lobby);
        gameActionRepository.deleteByLobby(lobby);
        gameStateRepository.delete(gameState);

        for (Player player : playerRepository.findByLobby(lobby)) {
            if (player.getPlayerToken() == null || GameRules.ELIMINATED_LEFT.equals(player.getEliminatedHow())) {
                playerRepository.delete(player);
                continue;
            }
            player.setIsAlive(true);
            player.setRole(null);
            player.setIsReady(false);
            player.setEliminatedHow(null);
            player.setEliminatedRound(null);
            playerRepository.save(player);
        }

        lobby.setStatus(GameRules.LOBBY_WAITING);
        lobby.setStartedAt(null);
        lobby.setEndedAt(null);
        lobbyRepository.save(lobby);

        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "REMATCH", "Nova igra u istoj sobi.", null);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    private void validateStartedLobbyRoleConfiguration(List<LobbyRole> lobbyRoles) {
        Set<Long> roleIds = new HashSet<>();

        for (LobbyRole lobbyRole : lobbyRoles) {
            if (lobbyRole.getRole() == null
                    || lobbyRole.getRole().getId() == null
                    || lobbyRole.getRoleCount() == null
                    || lobbyRole.getRoleCount() <= 0) {
                throw new ConflictException(ErrorCode.INVALID_ROLE_CONFIG, "Konfiguracija uloga lobija nije valjana.");
            }

            if (!roleIds.add(lobbyRole.getRole().getId())) {
                throw new ConflictException(ErrorCode.INVALID_ROLE_CONFIG, "Konfiguracija uloga sadrži duplikat uloge.");
            }
        }
    }

    public LobbyDetailsResponse getLobbyDetails(String code) {

        Lobby lobby = lobbyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));

        GameState gameState = gameStateRepository.findByLobby(lobby)
                .orElse(null);

        boolean finished = GameRules.LOBBY_FINISHED.equals(lobby.getStatus());
        List<LobbyPlayerResponse> playerResponses = playerRepository.findByLobby(lobby).stream()
                .sorted(Comparator.comparing(Player::getId))
                .map(player -> LobbyPlayerResponse.from(player, finished))
                .toList();

        List<LobbyRoleResponse> roles = lobbyRoleRepository.findByLobby(lobby).stream()
                .sorted(Comparator.comparing(lobbyRole -> lobbyRole.getRole().getId()))
                .map(lobbyRole -> new LobbyRoleResponse(
                        lobbyRole.getRole().getId(),
                        lobbyRole.getRole().getName(),
                        lobbyRole.getRoleCount()))
                .toList();

        return new LobbyDetailsResponse(
                lobby.getId(),
                lobby.getCode(),
                lobby.getStatus(),
                lobby.getMaxPlayers(),
                gameState != null ? gameState.getPhase() : null,
                gameState != null ? gameState.getRoundNumber() : null,
                playerResponses,
                roles,
                lobby.getHostPlayer() != null ? lobby.getHostPlayer().getId() : null,
                lobby.getMode(),
                lobby.getIsPublic(),
                lobby.getVoiceEnabled()
        );
    }

    /** Javne sobe koje čekaju igrače: najnovije prve, najviše 20 */
    public List<PublicLobbyResponse> getPublicLobbies() {
        return lobbyRepository.findTop20ByStatusAndIsPublicTrueOrderByCreatedAtDesc(GameRules.LOBBY_WAITING).stream()
                .map(lobby -> new PublicLobbyResponse(
                        lobby.getCode(),
                        lobby.getHostPlayer() != null ? lobby.getHostPlayer().getNickname() : null,
                        lobby.getHostPlayer() != null ? lobby.getHostPlayer().getAvatarEmoji() : null,
                        playerRepository.countByLobby(lobby),
                        lobby.getMaxPlayers(),
                        lobby.getMode(),
                        lobby.getCreatedAt()))
                .toList();
    }
}
