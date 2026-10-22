package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.repository.GameStateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Rokovi su na serveru: svake sekunde pronađe igre kojima je istekao rok faze ili poteza
 * i pomakne ih dalje. Rokovi su spremljeni u bazi (game_state.phase_ends_at),
 * pa se nakon ponovnog pokretanja servera igra nastavlja gdje je stala.
 * Uključuje se u SchedulingConfig (u testovima je isključen).
 */
@Component
@RequiredArgsConstructor
public class GameTimerService {

    private static final Logger log = LoggerFactory.getLogger(GameTimerService.class);

    private final GameStateRepository gameStateRepository;
    private final GamePhaseService gamePhaseService;

    @Scheduled(fixedDelay = 1000)
    public void processExpiredPhases() {
        for (GameState gameState : gameStateRepository.findByPhaseEndsAtBefore(OffsetDateTime.now())) {
            try {
                gamePhaseService.handleTimeout(gameState.getId());
            } catch (RuntimeException exception) {
                log.error("Istek roka nije obrađen za stanje igre {}", gameState.getId(), exception);
            }
        }
    }
}
