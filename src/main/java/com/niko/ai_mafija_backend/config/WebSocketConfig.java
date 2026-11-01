package com.niko.ai_mafija_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Jedina dopuštena pretplata: kod sobe je 6 znakova (LobbyService.LOBBY_CODE_CHARACTERS) */
    private static final Pattern LOBBY_TOPIC = Pattern.compile("/topic/lobby/[A-Z0-9]{6}");

    /** Server i klijent javljaju "živ sam" svakih 10 s, pa se tiho prekinuta veza (ugašen mobitel) otkrije */
    private static final long HEARTBEAT_MS = 10_000;

    private final String[] allowedOrigins;
    private final TaskScheduler heartbeatScheduler;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins,
            @Lazy TaskScheduler messageBrokerTaskScheduler) {
        // APP_CORS_ALLOWED_ORIGINS: više domena odvojenih zarezom, razmaci se ignoriraju
        this.allowedOrigins = Arrays.stream(allowedOrigins).map(String::trim).toArray(String[]::new);
        this.heartbeatScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
        config.setApplicationDestinationPrefixes("/app");
    }

    /** Događaje šalje samo server; klijent smije samo slušati točno jednu sobu. */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (stomp == null) {
                    return message;
                }
                if (StompCommand.SEND.equals(stomp.getCommand())) {
                    throw new MessagingException("Klijent ne smije slati poruke.");
                }
                if (StompCommand.SUBSCRIBE.equals(stomp.getCommand())
                        && (stomp.getDestination() == null || !LOBBY_TOPIC.matcher(stomp.getDestination()).matches())) {
                    throw new MessagingException("Nedopuštena pretplata.");
                }
                return message;
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }
}
