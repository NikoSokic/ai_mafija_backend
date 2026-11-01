package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.ReclaimDecisionRequest;
import com.niko.ai_mafija_backend.dto.Requests.ReclaimLobbyRequest;
import com.niko.ai_mafija_backend.dto.Response.ReclaimCreatedResponse;
import com.niko.ai_mafija_backend.dto.Response.ReclaimPendingResponse;
import com.niko.ai_mafija_backend.dto.Response.ReclaimResolvedResponse;
import com.niko.ai_mafija_backend.dto.Response.ReclaimStatusResponse;
import com.niko.ai_mafija_backend.exception.ConflictException;
import com.niko.ai_mafija_backend.exception.ErrorCode;
import com.niko.ai_mafija_backend.exception.ResourceNotFoundException;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.model.ReclaimRequest;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.ReclaimRequestRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.niko.ai_mafija_backend.model.ReclaimRequest.*;

/**
 * Povratak na novom ili ispražnjenom uređaju (token je samo na uređaju): igrač upiše kod sobe i nadimak, domaćin
 * odobri zahtjev, a uređaj koji ga je stvorio (tajnom claimSecret) dohvati novi token. Stari token vrijedi
 * sve dok novi uređaj ne preuzme mjesto.
 */
@Service
@RequiredArgsConstructor
public class ReclaimService {

    /** Koliko zahtjev čeka domaćina (i, nakon odobrenja, preuzimanje tokena) */
    static final Duration VALIDITY = Duration.ofMinutes(2);
    /** Istekli redovi se brišu tek nakon ovoga, da uređaj još vidi EXPIRED */
    private static final Duration KEEP_EXPIRED = Duration.ofHours(1);

    private final LobbyRepository lobbyRepository;
    private final PlayerRepository playerRepository;
    private final ReclaimRequestRepository reclaimRepository;
    private final PlayerAuthService playerAuthService;
    private final WebSocketService webSocketService;

    /** Novi uređaj traži povratak; domaćin dobiva događaj RECLAIM_REQUESTED. */
    @Transactional
    public ReclaimCreatedResponse request(String code, ReclaimLobbyRequest request) {
        Lobby lobby = findLobbyForUpdate(code);
        Player player = playerRepository.findByLobbyAndNicknameIgnoreCase(lobby, request.nickname().trim())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLAYER_NOT_FOUND,
                        "U sobi nema igrača s tim nadimkom."));
        // igrač koji je otišao (ili ga je domaćin izbacio) više nema token pa se ne vraća
        if (player.getPlayerToken() == null) {
            throw new ConflictException(ErrorCode.RECLAIM_NOT_ALLOWED, "Taj igrač je napustio sobu.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        reclaimRepository.deleteByExpiresAtBefore(now.minus(KEEP_EXPIRED));
        // novi zahtjev za istog igrača zamjenjuje prethodni
        for (ReclaimRequest earlier : reclaimRepository.findByPlayerIdAndStatus(player.getId(), PENDING)) {
            earlier.setStatus(EXPIRED);
            reclaimRepository.save(earlier);
        }

        ReclaimRequest saved = reclaimRepository.save(ReclaimRequest.builder()
                .lobbyId(lobby.getId())
                .playerId(player.getId())
                .claimSecret(UUID.randomUUID().toString())
                .status(PENDING)
                .createdAt(now)
                .expiresAt(now.plus(VALIDITY))
                .build());

        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "RECLAIM_REQUESTED",
                "Igrač se želi vratiti na novom uređaju.", toPending(saved, player));
        return new ReclaimCreatedResponse(saved.getId(), saved.getClaimSecret(), saved.getExpiresAt());
    }

    /** Domaćin odobrava ili odbija; ishod stiže novom uređaju kad ga upita. */
    @Transactional
    public void decide(String code, Long requestId, ReclaimDecisionRequest request) {
        Lobby lobby = findLobbyForUpdate(code);
        playerAuthService.authenticateHostForLobby(request.playerId(), request.playerToken(), lobby);
        ReclaimRequest reclaim = reclaimRepository.findByIdAndLobbyId(requestId, lobby.getId())
                .orElseThrow(this::notFound);
        if (!PENDING.equals(reclaim.getStatus()) || isExpired(reclaim)) {
            throw notFound();
        }

        reclaim.setStatus(Boolean.TRUE.equals(request.approve()) ? APPROVED : DENIED);
        reclaimRepository.save(reclaim);
        // ostali uređaji domaćina maknu pitanje
        webSocketService.publishLobbyEventAfterCommit(lobby.getCode(), "RECLAIM_RESOLVED",
                "Zahtjev za povratak je obrađen.", new ReclaimResolvedResponse(reclaim.getId(), reclaim.getStatus()));
    }

    /**
     * Novi uređaj pita za ishod. Tek kad je zahtjev odobren i prvi put se dohvati, igrač dobiva novi token
     * (stari se poništava) i zahtjev postaje CLAIMED.
     */
    @Transactional
    public ReclaimStatusResponse status(String code, Long requestId, String secret) {
        Lobby lobby = findLobbyForUpdate(code);
        ReclaimRequest reclaim = reclaimRepository.findByIdAndLobbyId(requestId, lobby.getId())
                .filter(found -> secretMatches(found.getClaimSecret(), secret))
                .orElseThrow(this::notFound);

        if ((PENDING.equals(reclaim.getStatus()) || APPROVED.equals(reclaim.getStatus())) && isExpired(reclaim)) {
            reclaim.setStatus(EXPIRED);
            reclaimRepository.save(reclaim);
        }
        if (!APPROVED.equals(reclaim.getStatus())) {
            return ReclaimStatusResponse.of(reclaim.getStatus());
        }

        Player player = playerRepository.findById(reclaim.getPlayerId())
                .filter(found -> found.getPlayerToken() != null)
                .orElse(null);
        if (player == null) {
            // igrač je u međuvremenu otišao ili ga je domaćin izbacio
            reclaim.setStatus(EXPIRED);
            reclaimRepository.save(reclaim);
            return ReclaimStatusResponse.of(EXPIRED);
        }

        player.setPlayerToken(UUID.randomUUID().toString());
        player.setLastSeenAt(OffsetDateTime.now());
        playerRepository.save(player);
        reclaim.setStatus(CLAIMED);
        reclaimRepository.save(reclaim);
        return new ReclaimStatusResponse(APPROVED, player.getId(), player.getNickname(), player.getIsAlive(),
                lobby.getId(), lobby.getCode(), player.getPlayerToken());
    }

    /** Zahtjevi koji čekaju domaćina; heartbeat ih vraća domaćinu, pa se ne gube ako je događaj promašen. */
    public List<ReclaimPendingResponse> pendingFor(Lobby lobby) {
        return reclaimRepository.findByLobbyIdAndStatusAndExpiresAtAfter(lobby.getId(), PENDING, OffsetDateTime.now())
                .stream()
                .map(reclaim -> playerRepository.findById(reclaim.getPlayerId())
                        .map(player -> toPending(reclaim, player))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private ReclaimPendingResponse toPending(ReclaimRequest reclaim, Player player) {
        return new ReclaimPendingResponse(
                reclaim.getId(), player.getId(), player.getNickname(), player.getAvatarEmoji(), reclaim.getExpiresAt());
    }

    private boolean isExpired(ReclaimRequest reclaim) {
        return reclaim.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    private static boolean secretMatches(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(), given.getBytes());
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(ErrorCode.RECLAIM_NOT_FOUND, "Zahtjev za povratak ne postoji ili je istekao.");
    }

    private Lobby findLobbyForUpdate(String code) {
        return lobbyRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOBBY_NOT_FOUND, "Lobby ne postoji."));
    }
}
