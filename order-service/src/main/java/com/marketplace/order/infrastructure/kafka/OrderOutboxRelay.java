package com.marketplace.order.infrastructure.kafka;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderOutboxRelay {
    private static final String TOPIC = "lucky-draw.events";

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OrderOutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    public void publish() {
        for (var event : unpublished()) {
            try {
                var record = new ProducerRecord<String, String>(TOPIC, event.id(), event.payload());
                record.headers().add("eventType", event.type().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                jdbc.update("UPDATE order_outbox SET published_at = NOW(6) WHERE id = ? AND published_at IS NULL", event.id());
            } catch (Exception e) {
                throw new IllegalStateException("Order outbox publish failed", e);
            }
        }
    }

    private List<Row> unpublished() {
        return jdbc.query("SELECT id, event_type, payload FROM order_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 50",
                (rs, rowNum) -> row(rs));
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getString("id"), rs.getString("event_type"), rs.getString("payload"));
    }

    private record Row(String id, String type, String payload) {}
}

