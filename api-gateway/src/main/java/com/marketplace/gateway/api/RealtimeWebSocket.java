package com.marketplace.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.RealtimeUpdate;
import com.marketplace.gateway.auth.DemoAuthService;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSocket
public class RealtimeWebSocket extends TextWebSocketHandler implements WebSocketConfigurer {
    private static final String USER_ID = "userId";
    private final DemoAuthService auth;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public RealtimeWebSocket(DemoAuthService auth, ObjectMapper json) {
        this.auth = auth;
        this.json = json;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this, "/ws/realtime");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String token = UriComponentsBuilder.fromUri(session.getUri()).build()
                    .getQueryParams().getFirst("access_token");
            var identity = auth.verify(token == null ? "" : token);
            session.getAttributes().put(USER_ID, identity.userId());
            sessions.computeIfAbsent(identity.userId(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
        } catch (RuntimeException exception) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        remove(session);
        session.close(CloseStatus.SERVER_ERROR);
    }

    @KafkaListener(topics = "lucky-draw.realtime", groupId = "api-gateway-realtime")
    public void broadcast(ConsumerRecord<String, String> record) throws Exception {
        var update = json.readValue(record.value(), RealtimeUpdate.class);
        for (var session : sessions.getOrDefault(update.userId(), Set.of())) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(record.value()));
                else remove(session);
            } catch (IOException exception) {
                remove(session);
            }
        }
    }

    private void remove(WebSocketSession session) {
        Object userId = session.getAttributes().get(USER_ID);
        if (userId == null) return;
        sessions.computeIfPresent(userId.toString(), (ignored, userSessions) -> {
            userSessions.remove(session);
            return userSessions.isEmpty() ? null : userSessions;
        });
    }
}
