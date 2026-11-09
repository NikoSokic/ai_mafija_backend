package com.niko.ai_mafija_backend.service;

import com.niko.ai_mafija_backend.model.TurnDuration;
import com.niko.ai_mafija_backend.repository.TurnDurationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static com.niko.ai_mafija_backend.service.GameRules.*;

/**
 * Trajanja stvarnih noćnih poteza i trajanje glumljenog poteza mrtve uloge.
 * Glumljeni potez traje kao nasumični nedavni stvarni potez iste vrste (iz svih soba),
 * pa se po trajanju ne razlikuje od poteza žive uloge.
 */
@Service
@RequiredArgsConstructor
public class TurnDurationService {

    private final TurnDurationRepository turnDurationRepository;
    private final GameRandom random;

    /** Spremi trajanje stvarnog poteza i zadrži samo zadnjih TURN_DURATION_KEEP te vrste */
    @Transactional
    public void record(String turn, Duration duration) {
        if (turn == null || duration == null || duration.isNegative()) {
            return;
        }
        turnDurationRepository.save(TurnDuration.builder()
                .turn(turn)
                .durationMs(duration.toMillis())
                .createdAt(OffsetDateTime.now())
                .build());
        // najnoviji id koji više ne stane među zadnjih TURN_DURATION_KEEP; on i stariji se brišu
        List<Long> firstTooOld = turnDurationRepository.findIdsNewestFirst(turn, PageRequest.of(TURN_DURATION_KEEP, 1));
        if (!firstTooOld.isEmpty()) {
            turnDurationRepository.deleteByTurnAndIdAtMost(turn, firstTooOld.getFirst());
        }
    }

    /**
     * Koliko traje glumljeni potez (nakon naracije): nasumično jedno od zadnjih TURN_DURATION_SAMPLE stvarnih
     * trajanja, a dok ih je manje od TURN_DURATION_MIN_SAMPLES – FAKE_TURN_FALLBACK. Uvijek unutar
     * [FAKE_TURN_MIN, najdulje moguće trajanje poteza].
     */
    public Duration fakeDuration(String turn) {
        List<TurnDuration> recent = turnDurationRepository.findNewest(turn, PageRequest.of(0, TURN_DURATION_SAMPLE));
        Duration duration = recent.size() < TURN_DURATION_MIN_SAMPLES
                ? random.between(FAKE_TURN_FALLBACK_MIN, FAKE_TURN_FALLBACK_MAX)
                : Duration.ofMillis(random.pick(recent).getDurationMs());
        Duration max = turnMaxDuration(turn);
        if (duration.compareTo(FAKE_TURN_MIN) < 0) {
            return FAKE_TURN_MIN;
        }
        return duration.compareTo(max) > 0 ? max : duration;
    }
}
