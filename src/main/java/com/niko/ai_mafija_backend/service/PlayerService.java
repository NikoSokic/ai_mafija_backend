package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.JoinLobbyRequest;
import com.niko.ai_mafija_backend.dto.Requests.KickPlayerRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Requests.PlayerRoleRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReconnectRequest;
import com.niko.ai_mafija_backend.dto.Response.JoinLobbyResponse;
import com.niko.ai_mafija_backend.dto.Response.LobbyPlayerResponse;
import com.niko.ai_mafija_backend.dto.Response.HostChangedResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerKickedResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerLeftResponse;
import com.niko.ai_mafija_backend.dto.Response.PlayerRoleResponse;
import com.niko.ai_mafija_backend.dto.Response.ReconnectResponse;
import com.niko.ai_mafija_backend.dto.Response.CurrentPlayerGameStateResponse;
import com.niko.ai_mafija_backend.exception.BadRequestException;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.exception.ForbiddenException;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.LobbyRoleRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.niko.ai_mafija_backend.service.GameRules.*;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;
    private final LobbyRoleRepository lobbyRoleRepository;
    private final PlayerAuthService playerAuthService;
    private final GameStateRepository gameStateRepository;
    private final WebSocketService webSocketService;
    private final GameStateService gameStateService;

    @Transactional
    public JoinLobbyResponse joinLobby(String code, JoinLobbyRequest request) {

        Lobby lobby = findLobbyForUpdate(code);
        String nickname = request.getNickname().trim();

        if (!lobby.getStatus().equals(LOBBY_WAITING)) {
            throw new ConflictException(ErrorCode.GAME_ALREADY_STARTED, "Igra je već započela ili završena.");
        }

        if (playerRepository.existsByLobbyAndNicknameIgnoreCase(lobby, nickname)) {
            throw new ConflictException(ErrorCode.NICKNAME_TAKEN, "Igrač s tim nicknameom već postoji u lobbyju.");
        }

        long currentPlayers = playerRepository.countByLobby(lobby);

        if (currentPlayers >= lobby.getMaxPlayers()) {
            throw new ConflictException(ErrorCode.LOBBY_FULL, "Lobby je popunjen.");
        }

        Player player = Player.builder()
                .lobby(lobby)
                .nickname(nickname)
                .avatarEmoji(LobbyService.cleanAvatar(request.getAvatarEmoji()))
                .isHost(false)
                .isAlive(true)
                .joinedAt(OffsetDateTime.now())
                .lastSeenAt(OffsetDateTime.now())
                .playerToken(UUID.randomUUID().toString())
                .build();

        Player savedPlayer = playerRepository.save(player);

        webSocketService.publishLobbyEventAfterCommit(
                lobby.getCode(),
                "PLAYER_JOINED",
                "Novi igrač se pridružio lobbyju.",
                LobbyPlayerResponse.from(savedPlayer, false)
        );
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());

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

        Lobby lobby = findLobby(code);
        boolean finished = LOBBY_FINISHED.equals(lobby.getStatus());

        return playerRepository.findByLobby(lobby).stream()
                .sorted(Comparator.comparing(Player::getId))
                .map(player -> LobbyPlayerResponse.from(player, finished))
                .toList();
    }

    public PlayerRoleResponse getPlayerRole(PlayerRoleRequest request) {

        Player player = playerAuthService.authenticate(
                request.getPlayerId(),
                request.getPlayerToken()
        );

        if (player.getRole() == null) {
            throw new ConflictException(ErrorCode.ROLE_NOT_ASSIGNED, "Uloga još nije dodijeljena.");
        }

        GameState gameState = gameStateRepository.findByLobby(player.getLobby()).orElse(null);
        return new PlayerRoleResponse(
                player.getId(),
                player.getNickname(),
                player.getRole().getName(),
                gameStateService.getCurrentPlayerState(player, gameState).teammates()
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

        CurrentPlayerGameStateResponse playerState = gameStateService.getCurrentPlayerState(player, gameState);

        return new ReconnectResponse(
                true,
                player.getId(),
                player.getNickname(),
                player.getIsAlive(),
                player.getIsHost(),
                lobby.getId(),
                lobby.getCode(),
                lobby.getStatus(),
                player.getRole() != null ? player.getRole().getName() : null,
                gameState != null ? gameState.getPhase() : null,
                gameState != null ? gameState.getRoundNumber() : null,
                // tko je na redu vidi samo igrač koji je na redu (inače se zna koje su uloge žive)
                playerState.canAct() ? gameState.getCurrentTurnRole() : null,
                playerState.canAct(),
                playerState.hasActed(),
                playerState.availableActions()
        );
    }

    /**
     * Igrač napušta sobu i gubi token.
     * U lobbyju se briše (prazna soba se briše cijela); tijekom igre ispada (LEFT), uloga ostaje tajna.
     * Ako ode domaćin, uloga domaćina prelazi na igrača koji je najranije ušao.
     */
    @Transactional
    public void leaveLobby(String code, PlayerAuthRequest request) {

        Lobby lobby = findLobbyForUpdate(code);
        Player player = playerAuthService.authenticate(request.playerId(), request.playerToken());
        if (!player.getLobby().getId().equals(lobby.getId())) {
            throw new ForbiddenException(ErrorCode.NOT_IN_LOBBY, "Igrač ne pripada ovom lobbyju.");
        }

        if (LOBBY_WAITING.equals(lobby.getStatus())) {
            leaveWaitingLobby(lobby, player);
        } else if (LOBBY_IN_PROGRESS.equals(lobby.getStatus())) {
            // tijekom igre se ne izlazi (ispadanje bi otkrilo ulogu i pomaknulo igru); klijent samo lokalno izlazi
            throw new ConflictException(ErrorCode.GAME_IN_PROGRESS, "Igra je u tijeku, sobu nije moguće napustiti.");
        } else {
            Player newHost = handOverHostIfNeeded(lobby, player);
            player.setPlayerToken(null);
            playerRepository.save(player);
            publishPlayerLeft(lobby, player, newHost);
        }
    }

    private void leaveWaitingLobby(Lobby lobby, Player player) {
        boolean lastPlayer = playerRepository.findByLobby(lobby).stream()
                .allMatch(other -> other.getId().equals(player.getId()));
        if (lastPlayer) {
            deleteLobby(lobby, player);
            publishPlayerLeft(lobby, player, null);
            return;
        }

        Player newHost = handOverHostIfNeeded(lobby, player);
        playerRepository.delete(player);
        publishPlayerLeft(lobby, player, newHost);
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    /**
     * Domaćin koji izlazi predaje ulogu: igrač koji je najranije ušao (prednost imaju živi koji nisu otišli);
     * null ako nema promjene. Kad nema nikoga, soba ostaje bez domaćina.
     */
    private Player handOverHostIfNeeded(Lobby lobby, Player leaving) {
        if (!Boolean.TRUE.equals(leaving.getIsHost())) {
            return null;
        }
        Player newHost = nextHost(lobby, leaving, other -> true);
        moveHost(lobby, leaving, newHost);
        return newHost;
    }

    /**
     * Domaćin se ne javlja: ulogu preuzima prvi prikladan igrač (isti redoslijed kao pri izlasku).
     * Bivši domaćin ostaje u sobi kao običan igrač. Ako nikoga nema, ništa se ne mijenja i vraća se null.
     */
    Player transferHost(Lobby lobby, Player host, Predicate<Player> eligible) {
        Player newHost = nextHost(lobby, host, eligible);
        if (newHost == null) {
            return null;
        }
        moveHost(lobby, host, newHost);
        webSocketService.publishLobbyEventAfterCommit(
                lobby.getCode(),
                "HOST_CHANGED",
                "Domaćin je promijenjen.",
                new HostChangedResponse(newHost.getId())
        );
        return newHost;
    }

    private Player nextHost(Lobby lobby, Player current, Predicate<Player> eligible) {
        return playerRepository.findByLobby(lobby).stream()
                .filter(other -> !other.getId().equals(current.getId()))
                .filter(other -> other.getPlayerToken() != null)
                .filter(eligible)
                .min(Comparator.comparing((Player other) -> !isAlive(other))
                        .thenComparing(Player::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Player::getId))
                .orElse(null);
    }

    private void moveHost(Lobby lobby, Player from, Player to) {
        from.setIsHost(false);
        lobby.setHostPlayer(to);
        if (to != null) {
            to.setIsHost(true);
            playerRepository.save(to);
        }
        playerRepository.save(from);
        lobbyRepository.save(lobby);
    }

    /** Domaćin izbacuje igrača koji je otišao (ili smeta) iz čekaonice; izbačeni se može opet pridružiti kodom. */
    @Transactional
    public void kickPlayer(String code, KickPlayerRequest request) {
        Lobby lobby = findLobbyForUpdate(code);
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);

        if (!LOBBY_WAITING.equals(lobby.getStatus())) {
            throw new ConflictException(ErrorCode.WRONG_PHASE, "Igrače je moguće izbaciti samo dok soba čeka igru.");
        }
        if (request.targetPlayerId().equals(request.playerId())) {
            throw new BadRequestException(ErrorCode.CANNOT_KICK_SELF, "Domaćin ne može izbaciti sam sebe.");
        }
        Player target = playerRepository.findById(request.targetPlayerId())
                .filter(other -> other.getLobby().getId().equals(lobby.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND, "Igrač nije u ovoj sobi."));

        playerRepository.delete(target);

        publishPlayerLeft(lobby, target, null);
        webSocketService.publishLobbyEventAfterCommit(
                lobby.getCode(),
                "PLAYER_KICKED",
                "Domaćin je izbacio igrača.",
                new PlayerKickedResponse(target.getId(), target.getNickname())
        );
        webSocketService.publishGameStateChangedAfterCommit(lobby.getCode());
    }

    private void deleteLobby(Lobby lobby, Player lastPlayer) {
        lobby.setHostPlayer(null);
        lobbyRepository.save(lobby);
        lobbyRoleRepository.deleteAll(lobbyRoleRepository.findByLobby(lobby));
        gameStateRepository.findByLobby(lobby).ifPresent(gameStateRepository::delete);
        playerRepository.delete(lastPlayer);
        lobbyRepository.delete(lobby);
    }

    private void publishPlayerLeft(Lobby lobby, Player player, Player newHost) {
        webSocketService.publishLobbyEventAfterCommit(
                lobby.getCode(),
                "PLAYER_LEFT",
                "Igrač je napustio sobu.",
                new PlayerLeftResponse(player.getId(), player.getNickname(), newHost != null ? newHost.getId() : null)
        );
        if (newHost != null) {
            webSocketService.publishLobbyEventAfterCommit(
                    lobby.getCode(),
                    "HOST_CHANGED",
                    "Domaćin je promijenjen.",
                    new HostChangedResponse(newHost.getId())
            );
        }
    }

    private Lobby findLobbyForUpdate(String code) {
        return lobbyRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
    }

    private Lobby findLobby(String code) {
        return lobbyRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
    }
}
