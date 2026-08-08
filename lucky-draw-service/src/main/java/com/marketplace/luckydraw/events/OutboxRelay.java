package com.marketplace.luckydraw.events;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) { this.jdbc = jdbc; this.kafka = kafka; }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    public void publish() {
        var events = jdbc.query("SELECT id,event_type,payload FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 100",
                (rs, n) -> new Event(rs.getString("id"), rs.getString("event_type"), rs.getString("payload")));
        for (var event : events) {
            try {
                var record = new ProducerRecord<String, String>("lucky-draw.events", event.id(), event.payload());
                record.headers().add("eventType", event.type().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
                jdbc.update("UPDATE outbox SET published_at=NOW(6) WHERE id=? AND published_at IS NULL", event.id());
            } catch (Exception exception) { throw new IllegalStateException("Lucky draw outbox publish failed", exception); }
        }
    }
    private record Event(String id, String type, String payload) {}
}
