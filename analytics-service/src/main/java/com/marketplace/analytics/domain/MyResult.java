package com.marketplace.analytics.domain;

import com.marketplace.events.Reward;
import java.time.Instant;
import java.util.List;

public record MyResult(
        String campaignId,
        List<String> entryIds,
        int remainingQuota,
        String winnerEntryId,
        boolean won,
        Reward reward,
        Instant lastUpdatedAt
) {}
