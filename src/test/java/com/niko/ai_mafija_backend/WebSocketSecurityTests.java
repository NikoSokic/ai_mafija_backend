package com.niko.ai_mafija_backend;

import com.niko.ai_mafija_backend.dto.Requests.ActionRequest;
import com.niko.ai_mafija_backend.model.GameState;
import com.niko.ai_mafija_backend.model.Lobby;
import com.niko.ai_mafija_backend.model.Player;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Klijent ne smije slati događaje u sobu ni slušati više soba odjednom; potez uloge ne javlja svima
 * da je uloga živa; server javlja heartbeat
 */
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
    void serverAdvertisesHeartbeat() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new SimpleMessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        client.setTaskScheduler(scheduler);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:5173");
        StompHeaders connect = new StompHeaders();
        connect.setHeartbeat(new long[]{10000, 10000});
        CompletableFuture<long[]> beat = new CompletableFuture<>();
        client.connectAsync("ws://localhost:" + port + "/ws/websocket", headers, connect,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connected) {
                        beat.complete(connected.getHeartbeat());
                    }
                });
        assertArrayEquals(new long[]{10000, 10000}, beat.get(5, TimeUnit.SECONDS));
        scheduler.shutdown();
    }

    @Test
    void doctorTurnDoesNotBroadcastGameState() throws Exception {
        Lobby lobby = lobby();
        lobby.setCode("NIGHT1");
        lobbyRepository.save(lobby);
        host(lobby, "Mafia");
        Player doktor = player(lobby, "Doktor", true);
        Player target = player(lobby, "Građanin", true);
        player(lobby, "Građanin", true);
        GameState state = state(lobby, "NIGHT", 2);
        state.setCurrentTurnRole("Doktor");
        gameStateRepository.save(state);

        StompSession observer = connect();
        BlockingQueue<String> inbox = subscribe(observer, "/topic/lobby/NIGHT1");

        ActionRequest request = new ActionRequest();
        request.setPlayerId(doktor.getId());
        request.setPlayerToken(doktor.getPlayerToken());
        request.setActionType("SAVE");
        request.setTargetPlayerId(target.getId());
        actionService.submitAction(request);

        assertNull(inbox.poll(2, TimeUnit.SECONDS), "Potez Doktora je poslao događaj svima");
        observer.disconnect();
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
