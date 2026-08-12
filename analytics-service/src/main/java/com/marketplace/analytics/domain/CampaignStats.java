package com.marketplace.analytics.domain;

import java.time.Instant;

public record CampaignStats(
        String campaignId,
        String name,
        String status,
        long totalEntries,
        long distinctParticipants,
        String winnerEntryId,
        String winnerUserId,
        String snapshotHash,
        Instant lastUpdatedAt
) {}
