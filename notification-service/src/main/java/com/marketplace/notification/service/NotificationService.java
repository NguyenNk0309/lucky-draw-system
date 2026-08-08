package com.marketplace.notification.service;

import com.marketplace.events.WinnerPicked;
import com.marketplace.notification.domain.Notification;
import com.marketplace.notification.domain.port.NotificationProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final NotificationProvider provider;

    public NotificationService(JdbcTemplate jdbc, NotificationProvider provider) {
        this.jdbc = jdbc;
        this.provider = provider;
    }

    @Transactional
    public void notifyWinner(WinnerPicked event) {
        int first = jdbc.update("INSERT IGNORE INTO processed_events (event_id,consumer_name) VALUES (?,'notification-service')",
                event.eventId().toString());
        if (first == 0) return;
        provider.send(new Notification(UUID.randomUUID().toString(), event.campaignId(), event.winnerUserId(),
                "You won campaign " + event.campaignId(), event.occurredAt()));
    }

    public List<Notification> list(String userId) {
        return provider.findByUser(userId);
    }
}

