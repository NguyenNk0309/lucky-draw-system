package com.marketplace.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.EntrySubmitted;
import com.marketplace.events.RealtimeUpdate;
import com.marketplace.events.WinnerPicked;
import com.marketplace.notification.domain.Notification;
import com.marketplace.notification.domain.port.NotificationProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final NotificationProvider provider;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public NotificationService(JdbcTemplate jdbc, NotificationProvider provider,
            KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.kafka = kafka;
        this.json = json;
    }

    @Transactional
    public void notifySubmitted(EntrySubmitted event) {
        if (!first(event.eventId().toString())) return;
        provider.send(new Notification(UUID.randomUUID().toString(), event.campaignId(), event.entryId(),
                event.userId(), "Ticket submitted. Entry #" + event.sequence() + " is in the campaign draw.",
                event.occurredAt()));
        publishAfterCommit(event.eventId(), event.occurredAt(), event.userId());
    }

    @Transactional
    public void notifyWinner(WinnerPicked event) {
        if (!first(event.eventId().toString())) return;
        provider.send(new Notification(UUID.randomUUID().toString(), event.campaignId(), event.winnerEntryId(),
                event.winnerUserId(),
                "You won " + event.reward().type() + " " + event.reward().reference() + ".",
                event.occurredAt()));
        publishAfterCommit(event.eventId(), event.occurredAt(), event.winnerUserId());
    }

    public List<Notification> list(String userId) {
        return provider.findByUser(userId);
    }

    private boolean first(String eventId) {
        return jdbc.update("""
                INSERT IGNORE INTO processed_events (event_id,consumer_name)
                VALUES (?,'notification-service')
                """, eventId) == 1;
    }

    private void publishAfterCommit(UUID eventId, java.time.Instant occurredAt, String userId) {
        var update = new RealtimeUpdate(eventId, occurredAt, userId, RealtimeUpdate.Type.NOTIFICATION);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish(update); }
            });
        } else {
            publish(update);
        }
    }

    private void publish(RealtimeUpdate update) {
        try {
            kafka.send("lucky-draw.realtime", update.userId(), json.writeValueAsString(update));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize realtime update", exception);
        }
    }
}
