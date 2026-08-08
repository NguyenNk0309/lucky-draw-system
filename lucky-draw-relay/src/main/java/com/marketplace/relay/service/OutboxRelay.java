package com.marketplace.relay.service;

import com.marketplace.relay.infrastructure.OutboxStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelay {
    private final OutboxStore outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxStore outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    public void publish() {
        for (var event : outbox.unpublished()) {
            try {
                var record = new ProducerRecord<String, String>("lucky-draw.events", event.id(), event.payload());
                record.headers().add("eventType", event.eventType().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
                outbox.markPublished(event.id());
            } catch (Exception e) {
                throw new IllegalStateException("Lucky draw outbox publish failed", e);
            }
        }
    }
}

