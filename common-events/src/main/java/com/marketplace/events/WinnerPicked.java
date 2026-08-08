package com.marketplace.events;

import java.time.Instant;
import java.util.UUID;

public record WinnerPicked(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String campaignId,
        String winnerEntryId,
        String winnerUserId,
        String snapshotHash,
        Reward reward
) {}

