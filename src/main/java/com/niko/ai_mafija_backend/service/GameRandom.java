package com.niko.ai_mafija_backend.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Slučajnost u noći (pauza nakon poteza, trajanje glumljenog poteza, nasumična meta mafije) na jednom mjestu,
 * da je testovi mogu zamijeniti predvidljivom (use / reset).
 */
@Component
public class GameRandom {

    private volatile Random random = new SecureRandom();

    /** Nasumični element neprazne liste */
    public <T> T pick(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }

    /** Nasumično trajanje između min i max, uključivo (na milisekundu) */
    public Duration between(Duration min, Duration max) {
        return Duration.ofMillis(random.nextLong(min.toMillis(), max.toMillis() + 1));
    }

    /** Samo za testove: zadani izvor slučajnosti */
    public void use(Random replacement) {
        random = replacement;
    }

    /** Samo za testove: natrag na pravi izvor slučajnosti */
    public void reset() {
        random = new SecureRandom();
    }
}
