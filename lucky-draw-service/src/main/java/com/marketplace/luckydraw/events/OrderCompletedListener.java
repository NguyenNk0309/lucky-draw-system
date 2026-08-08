package com.marketplace.luckydraw.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.OrderCompleted;
import com.marketplace.luckydraw.service.TicketService;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedListener {
    private final TicketService tickets;
    private final ObjectMapper json;

    public OrderCompletedListener(TicketService tickets, ObjectMapper json) {
        this.tickets = tickets;
        this.json = json;
    }

    @KafkaListener(topics = "lucky-draw.events")
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        var header = record.headers().lastHeader("eventType");
        if (header == null || !"OrderCompleted".equals(new String(header.value(), StandardCharsets.UTF_8))) return;
        var event = json.readValue(record.value(), OrderCompleted.class);
        tickets.issueForOrder(event.orderId(), event.userId());
    }
}

