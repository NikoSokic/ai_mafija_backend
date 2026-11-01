package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.dto.Requests.PlayerAuthRequest;
import com.niko.ai_mafija_backend.dto.Response.HeartbeatResponse;
import com.niko.ai_mafija_backend.dto.Response.ReclaimPendingResponse;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Tko je još tu: klijenti se javljaju svakih 15 s (heartbeat), a posao svakih nekoliko sekundi predaje
 * ulogu domaćina drugom igraču kad se domaćin predugo ne javlja (čekaonica i kraj igre 120 s, igra 45 s).
 * Uključuje se u SchedulingConfig (u testovima je isključen, pa ih pokreću sami).
 */
@Service
@RequiredArgsConstructor
public class HostPresenceService {

    private static final Logger log = LoggerFactory.getLogger(HostPresenceService.class);

    private final PlayerAuthService playerAuthService;
    private final PlayerService playerService;
    private final ReclaimService reclaimService;
    private final PlayerRepository playerRepository;
    private final LobbyRepository lobbyRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * Nakon (ponovnog) pokretanja servera nitko se još nije javio, pa se svi računaju od ovog trenutka.
     * Inače bi odmah nakon restarta svi bili "Nije tu", a domaćin bi izgubio ulogu.
     */
    private final OffsetDateTime startedAt = OffsetDateTime.now();

    /** Igrač je živ i javlja se; odgovor kaže tko je domaćin i tko je "Nije tu" */
    public HeartbeatResponse heartbeat(PlayerAuthRequest request) {
        Player player = playerAuthService.authenticate(request.playerId(), request.playerToken());
        OffsetDateTime now = OffsetDateTime.now();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> playerRepository.touch(player.getId(), now));

        Lobby lobby = lobbyRepository.findById(player.getLobby().getId()).orElseThrow();
        Long hostId = lobby.getHostPlayer() != null ? lobby.getHostPlayer().getId() : null;
        // sebe nikad ne označava: upravo se javio
        List<Long> away = awayPlayerIds(lobby, now).stream().filter(id -> !id.equals(player.getId())).toList();
        // zahtjeve za povratak na novom uređaju vidi samo domaćin
        List<ReclaimPendingResponse> reclaims = player.getId().equals(hostId) ? reclaimService.pendingFor(lobby) : List.of();
        return new HeartbeatResponse(hostId, away, reclaims);
    }

    /** Igrači s tokenom koji se ne javljaju AWAY_AFTER ("Nije tu") */
    public List<Long> awayPlayerIds(Lobby lobby, OffsetDateTime now) {
        return playerRepository.findByLobby(lobby).stream()
                .filter(player -> player.getPlayerToken() != null)
                .filter(player -> isAbsent(player, AWAY_AFTER, now))
                .map(Player::getId)
                .sorted()
                .toList();
    }

    @Scheduled(fixedDelay = 5000)
    public void transferAbsentHosts() {
        transferAbsentHosts(OffsetDateTime.now());
    }

    /** Odvojeno od rasporeda da testovi mogu zadati "sada" */
    public void transferAbsentHosts(OffsetDateTime now) {
        for (Lobby lobby : lobbyRepository.findByHostPlayerIsNotNull()) {
            if (!isAbsent(lobby.getHostPlayer(), hostLimit(lobby), now)) {
                continue;
            }
            try {
                transferHost(lobby.getCode(), now);
            } catch (RuntimeException exception) {
                log.error("Domaćin sobe {} nije predan", lobby.getCode(), exception);
            }
        }
    }

    private void transferHost(String code, OffsetDateTime now) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // zaključano kao izlazak iz sobe; iz zaključanog reda se ponovno provjerava je li domaćin još odsutan
            Lobby lobby = lobbyRepository.findByCodeForUpdate(code).orElse(null);
            if (lobby == null || lobby.getHostPlayer() == null) {
                return;
            }
            Player host = lobby.getHostPlayer();
            if (!isAbsent(host, hostLimit(lobby), now)) {
                return;
            }
            Player newHost = playerService.transferHost(lobby, host, other -> !isAbsent(other, AWAY_AFTER, now));
            if (newHost != null) {
                log.info("Soba {}: domaćin {} se ne javlja, novi domaćin je {}", code, host.getId(), newHost.getId());
            }
        });
    }

    private static Duration hostLimit(Lobby lobby) {
        return LOBBY_IN_PROGRESS.equals(lobby.getStatus()) ? HOST_ABSENT_IN_GAME : HOST_ABSENT_LOBBY;
    }

    private boolean isAbsent(Player player, Duration limit, OffsetDateTime now) {
        OffsetDateTime seen = player.getLastSeenAt();
        if (seen == null || seen.isBefore(startedAt)) {
            seen = startedAt;
        }
        return seen.plus(limit).isBefore(now);
    }
}
