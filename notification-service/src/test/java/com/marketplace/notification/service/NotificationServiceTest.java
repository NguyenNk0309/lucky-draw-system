package com.marketplace.notification.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.EntrySubmitted;
import com.marketplace.notification.domain.port.NotificationProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationServiceTest {
    @Test
    void submittedEntryCreatesNotificationAndRealtimeSignal() {
        var jdbc = mock(JdbcTemplate.class);
        var provider = mock(NotificationProvider.class);
        @SuppressWarnings("unchecked")
        var kafka = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        when(jdbc.update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(1);
        var service = new NotificationService(jdbc, provider, kafka,
                new ObjectMapper().findAndRegisterModules());
        var event = new EntrySubmitted(UUID.randomUUID(), Instant.parse("2026-08-12T00:00:00Z"),
                "entry-1", "correlation", "entry-1", "campaign-1", "customer-1", "ticket-1", 4, 5);

        service.notifySubmitted(event);

        verify(provider).send(argThat(notification -> notification.message().contains("Entry #4")));
        verify(kafka).send(eq("lucky-draw.realtime"), eq("customer-1"),
                argThat(payload -> payload.contains("NOTIFICATION")));
    }
}
