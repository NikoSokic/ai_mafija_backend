package com.niko.ai_mafija_backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Uključuje GameTimerService (rokovi rasprave, glasanja i poteza noći).
 * U testovima je app.timers.enabled=false, da testovi sami pomiču vrijeme.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.timers.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
