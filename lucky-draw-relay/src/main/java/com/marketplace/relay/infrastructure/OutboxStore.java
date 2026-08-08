package com.marketplace.relay.infrastructure;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxStore {
    private final JdbcTemplate jdbc;

    public OutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> unpublished() {
        return jdbc.query("SELECT id,event_type,payload FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 100",
                (rs, n) -> new Row(rs.getString("id"), rs.getString("event_type"), rs.getString("payload")));
    }

    public void markPublished(String id) {
        jdbc.update("UPDATE outbox SET published_at=NOW(6) WHERE id=? AND published_at IS NULL", id);
    }

    public record Row(String id, String eventType, String payload) {}
}

