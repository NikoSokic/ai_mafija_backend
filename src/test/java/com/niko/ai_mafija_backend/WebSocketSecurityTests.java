package com.niko.ai_mafija_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;

/** Klijent ne smije slati događaje u sobu ni slušati više soba odjednom */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketSecurityTests extends GameTestSupport {

    @LocalServerPort private int port;

    private StompSession connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new SimpleMessageConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:5173");
        return client.connectAsync("ws://localhost:" + port + "/ws/websocket", headers,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> subscribe(StompSession session, String destination) throws Exception {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        Thread.sleep(300);
        return queue;
    }

    @Test
    void clientCannotForgeEventsOrEavesdropOnOtherRooms() throws Exception {
        StompSession victim = connect();
        BlockingQueue<String> inbox = subscribe(victim, "/topic/lobby/ABC123");

        StompSession attacker = connect();
        BlockingQueue<String> everything = subscribe(attacker, "/topic/lobby/**");

        StompHeaders send = new StompHeaders();
        send.setDestination("/topic/lobby/ABC123");
        send.setContentType(MimeTypeUtils.APPLICATION_JSON);
        try {
            attacker.send(send, "{\"type\":\"REMATCH\",\"lobbyCode\":\"ABC123\"}".getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            // veza se može zatvoriti čim server odbije poruku
        }

        assertNull(inbox.poll(2, TimeUnit.SECONDS), "Lažni događaj je stigao igraču");
        assertNull(everything.poll(1, TimeUnit.SECONDS), "Pretplata na sve sobe prima događaje");
        try {
            victim.disconnect();
        } catch (RuntimeException ignored) {
        }
    }
}
