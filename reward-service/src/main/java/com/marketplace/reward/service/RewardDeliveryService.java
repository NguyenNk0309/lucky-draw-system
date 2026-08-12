package com.marketplace.reward.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.RealtimeUpdate;
import com.marketplace.events.WinnerPicked;
import com.marketplace.reward.domain.RewardClaim;
import com.marketplace.reward.infrastructure.RewardClaimRepository;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class RewardDeliveryService {
    private final RewardClaimRepository claims;
    private final RewardStrategyFactory strategies;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public RewardDeliveryService(RewardClaimRepository claims, RewardStrategyFactory strategies,
            KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.claims = claims;
        this.strategies = strategies;
        this.kafka = kafka;
        this.json = json;
    }

    public void deliver(WinnerPicked event) {
        claims.createOnce(event).or(() -> claims.findUndeliveredByEntry(event.winnerEntryId())).ifPresent(claim -> {
            var result = strategies.forReward(event.reward()).deliver(event.winnerUserId(), event.campaignId());
            claims.markDelivered(claim.id(), result.deliveryReference());
            publish(new RealtimeUpdate(event.eventId(), event.occurredAt(), event.winnerUserId(),
                    RealtimeUpdate.Type.REWARD));
        });
    }

    public List<RewardClaim> list(String userId) {
        return claims.findByUser(userId);
    }

    private void publish(RealtimeUpdate update) {
        try {
            kafka.send("lucky-draw.realtime", update.userId(), json.writeValueAsString(update));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize realtime update", exception);
        }
    }
}
