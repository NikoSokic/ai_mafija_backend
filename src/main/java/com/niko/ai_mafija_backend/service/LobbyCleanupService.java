package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.repository.GameActionRepository;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import com.niko.ai_mafija_backend.repository.LobbyRepository;
import com.niko.ai_mafija_backend.repository.LobbyRoleRepository;
import com.niko.ai_mafija_backend.repository.PlayerRepository;
import com.niko.ai_mafija_backend.repository.ReclaimRequestRepository;
import com.niko.ai_mafija_backend.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

import static com.niko.ai_mafija_backend.service.GameRules.ABANDONED_AFTER;

/**
 * Napuštene sobe: posao svakih 5 min trajno briše sobu u kojoj se nijedan igrač nije javio ABANDONED_AFTER,
 * bez obzira na to je li čekaonica, igra u tijeku ili kraj igre. Inače bi se napuštena igra vrtjela zauvijek.
 * Uključuje se u SchedulingConfig (u testovima je isključen, pa ga pokreću sami).
 */
@Service
@RequiredArgsConstructor
public class LobbyCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LobbyCleanupService.class);

    private final LobbyRepository lobbyRepository;
    private final LobbyRoleRepository lobbyRoleRepository;
    private final PlayerRepository playerRepository;
    private final GameStateRepository gameStateRepository;
    private final GameActionRepository gameActionRepository;
    private final VoteRepository voteRepository;
    private final ReclaimRequestRepository reclaimRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * Dok server ne radi, nitko se ne može javiti. Zato se u prvom ABANDONED_AFTER nakon (ponovnog)
     * pokretanja ništa ne briše, da duži prekid rada servera ne obriše igre koje su još u tijeku.
     */
    private final OffsetDateTime startedAt = OffsetDateTime.now();

    @Scheduled(fixedDelay = 300_000)
    public void removeAbandonedLobbies() {
        removeAbandonedLobbies(OffsetDateTime.now());
    }

    /** Odvojeno od rasporeda da testovi mogu zadati "sada" */
    public void removeAbandonedLobbies(OffsetDateTime now) {
        OffsetDateTime since = now.minus(ABANDONED_AFTER);
        if (since.isBefore(startedAt)) {
            return;
        }
        for (String code : lobbyRepository.findCodesWithoutActivitySince(since)) {
            try {
                removeLobby(code, since);
            } catch (RuntimeException exception) {
                log.error("Napuštena soba {} nije obrisana", code, exception);
            }
        }
    }

    private void removeLobby(String code, OffsetDateTime since) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // zaključano kao ulazak u sobu; iz zaključanog reda se ponovno provjerava je li se netko u međuvremenu javio
            Lobby lobby = lobbyRepository.findByCodeForUpdate(code).orElse(null);
            if (lobby == null || playerRepository.existsActivitySince(lobby, since)) {
                return;
            }
            lobby.setHostPlayer(null);
            lobbyRepository.saveAndFlush(lobby);

            reclaimRepository.deleteByLobbyId(lobby.getId());
            voteRepository.deleteByLobby(lobby);
            gameActionRepository.deleteByLobby(lobby);
            gameStateRepository.findByLobby(lobby).ifPresent(gameStateRepository::delete);
            lobbyRoleRepository.deleteAll(lobbyRoleRepository.findByLobby(lobby));
            playerRepository.deleteAll(playerRepository.findByLobby(lobby));
            lobbyRepository.delete(lobby);
            log.info("Soba {} je obrisana: nitko se nije javio od {}", code, since);
        });
    }
}
