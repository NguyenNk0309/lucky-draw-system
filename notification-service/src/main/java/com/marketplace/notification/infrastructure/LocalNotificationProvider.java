package com.marketplace.notification.infrastructure;

import com.marketplace.notification.domain.Notification;
import com.marketplace.notification.domain.port.NotificationProvider;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LocalNotificationProvider implements NotificationProvider {
    private final JdbcTemplate jdbc;

    public LocalNotificationProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void send(Notification notification) {
        jdbc.update("INSERT IGNORE INTO notifications (id,campaign_id,user_id,message,sent_at) VALUES (?,?,?,?,?)",
                notification.id(), notification.campaignId(), notification.userId(), notification.message(),
                Timestamp.from(notification.sentAt()));
    }

    @Override
    public List<Notification> findByUser(String userId) {
        return jdbc.query("SELECT * FROM notifications WHERE user_id=? ORDER BY sent_at DESC", (rs, n) ->
                new Notification(rs.getString("id"), rs.getString("campaign_id"), rs.getString("user_id"),
                        rs.getString("message"), rs.getTimestamp("sent_at").toInstant()), userId);
    }
}
