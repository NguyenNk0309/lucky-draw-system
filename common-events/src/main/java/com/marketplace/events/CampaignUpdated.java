package com.marketplace.events;

import java.time.Instant;
import java.util.UUID;

public record CampaignUpdated(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String campaignId,
        String sellerId,
        String name,
        String status,
        int maxEntriesPerUser,
        Instant startAt,
        Instant endAt,
        Reward reward
) {}

