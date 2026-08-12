package com.marketplace.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.EntrySubmitted;
import com.marketplace.events.WinnerPicked;
import com.marketplace.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LuckyDrawEventListener {
    private final NotificationService service;
    private final ObjectMapper json;

    public LuckyDrawEventListener(NotificationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "lucky-draw.events")
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        var header = record.headers().lastHeader("eventType");
        if (header == null) return;
        switch (new String(header.value(), StandardCharsets.UTF_8)) {
            case "EntrySubmitted" -> service.notifySubmitted(json.readValue(record.value(), EntrySubmitted.class));
            case "WinnerPicked" -> service.notifyWinner(json.readValue(record.value(), WinnerPicked.class));
            default -> { }
        }
    }
}
