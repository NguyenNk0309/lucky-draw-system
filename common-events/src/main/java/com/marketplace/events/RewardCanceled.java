package com.marketplace.events;

import java.time.Instant;
import java.util.UUID;

public record RewardCanceled(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String campaignId,
        String entryId,
        String userId,
        Reward reward
) {}
