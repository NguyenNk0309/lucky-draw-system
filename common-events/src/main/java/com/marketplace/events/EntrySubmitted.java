package com.marketplace.events;

import java.time.Instant;
import java.util.UUID;

public record EntrySubmitted(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String entryId,
        String campaignId,
        String userId,
        String ticketId,
        long sequence,
        int maxEntriesPerUser
) {}
