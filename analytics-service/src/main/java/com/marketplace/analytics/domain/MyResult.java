package com.marketplace.analytics.domain;

import java.time.Instant;
import java.util.List;

public record MyResult(
        String campaignId,
        List<String> entryIds,
        int remainingQuota,
        String winnerEntryId,
        boolean won,
        Instant lastUpdatedAt
) {}

