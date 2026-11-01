package com.niko.ai_mafija_backend.config;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentry sam skriva samo standardna osjetljiva zaglavlja (Authorization, Cookie), a naš token igrača je
 * prilagođeno zaglavlje, pa se ovdje uklanja iz svakog događaja prije slanja.
 */
@Configuration
public class SentryConfig {

    private static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> {
            Request request = event.getRequest();
            if (request != null && request.getHeaders() != null) {
                request.getHeaders().keySet().removeIf(PLAYER_TOKEN_HEADER::equalsIgnoreCase);
            }
            return event;
        };
    }
}
