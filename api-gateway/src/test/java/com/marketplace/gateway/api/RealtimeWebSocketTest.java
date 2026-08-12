package com.marketplace.gateway.api;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.RealtimeUpdate;
import com.marketplace.gateway.auth.DemoAuthService;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class RealtimeWebSocketTest {
    @Test
    void sendsOnlyAuthenticatedUsersTheirRealtimeUpdate() throws Exception {
        var auth = mock(DemoAuthService.class);
        var session = mock(WebSocketSession.class);
        when(auth.verify("token")).thenReturn(new DemoAuthService.Identity("customer-1", "CUSTOMER"));
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/realtime?access_token=token"));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        var json = new ObjectMapper().findAndRegisterModules();
        var socket = new RealtimeWebSocket(auth, json);
        socket.afterConnectionEstablished(session);
        var update = new RealtimeUpdate(UUID.randomUUID(), Instant.parse("2026-08-12T00:00:00Z"),
                "customer-1", RealtimeUpdate.Type.NOTIFICATION);

        socket.broadcast(new ConsumerRecord<>("lucky-draw.realtime", 0, 0, "customer-1",
                json.writeValueAsString(update)));

        verify(session).sendMessage(argThat(message -> message.getPayload().toString().contains("NOTIFICATION")));
    }
}
