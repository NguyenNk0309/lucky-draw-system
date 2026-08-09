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
        int pendingRewards,
        int releasedRewards,
        int canceledRewards,
        String rewardStatus,
        Reward reward,
        Instant lastUpdatedAt
) {}
