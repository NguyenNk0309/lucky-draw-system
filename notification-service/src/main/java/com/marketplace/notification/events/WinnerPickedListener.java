package com.marketplace.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.WinnerPicked;
import com.marketplace.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WinnerPickedListener {
    private final NotificationService service;
    private final ObjectMapper json;

    public WinnerPickedListener(NotificationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "lucky-draw.events")
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        var header = record.headers().lastHeader("eventType");
        if (header == null || !"WinnerPicked".equals(new String(header.value(), StandardCharsets.UTF_8))) return;
        service.notifyWinner(json.readValue(record.value(), WinnerPicked.class));
    }
}

