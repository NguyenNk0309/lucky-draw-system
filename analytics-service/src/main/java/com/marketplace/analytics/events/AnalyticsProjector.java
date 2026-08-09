package com.marketplace.analytics.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.analytics.domain.port.ReadModelRepository;
import com.marketplace.events.CampaignUpdated;
import com.marketplace.events.EntrySubmitted;
import com.marketplace.events.RewardCanceled;
import com.marketplace.events.WinnerPicked;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsProjector {
    private final ReadModelRepository readModel;
    private final ObjectMapper json;

    public AnalyticsProjector(ReadModelRepository readModel, ObjectMapper json) {
        this.readModel = readModel;
        this.json = json;
    }

    @KafkaListener(topics = "lucky-draw.events")
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        var header = record.headers().lastHeader("eventType");
        if (header == null) return;
        switch (new String(header.value(), StandardCharsets.UTF_8)) {
            case "CampaignUpdated" -> readModel.project(json.readValue(record.value(), CampaignUpdated.class));
            case "EntrySubmitted" -> readModel.project(json.readValue(record.value(), EntrySubmitted.class));
            case "RewardCanceled" -> readModel.project(json.readValue(record.value(), RewardCanceled.class));
            case "WinnerPicked" -> readModel.project(json.readValue(record.value(), WinnerPicked.class));
            default -> { }
        }
    }
}
