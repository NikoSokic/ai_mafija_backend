package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.config.SentryConfig;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Token igrača nikad ne smije otići u Sentry. */
class SentryConfigTests {

    @Test
    void beforeSendRemovesPlayerTokenButKeepsOtherHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-player-token", "tajna");
        headers.put("X-Player-Id", "7");
        Request request = new Request();
        request.setHeaders(headers);
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        SentryEvent result = new SentryConfig().sentryBeforeSend().execute(event, new Hint());

        assertNotNull(result);
        assertFalse(result.getRequest().getHeaders().containsKey("x-player-token"));
        assertEquals("7", result.getRequest().getHeaders().get("X-Player-Id"));
    }

    @Test
    void beforeSendHandlesEventWithoutRequest() {
        SentryEvent event = new SentryEvent();
        assertNotNull(new SentryConfig().sentryBeforeSend().execute(event, new Hint()));
    }
}
