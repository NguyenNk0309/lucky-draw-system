package com.marketplace.reward.domain;

import java.time.Instant;

public record RewardClaim(
        String id,
        String campaignId,
        String winnerUserId,
        String rewardType,
        String reference,
        String deliveryReference,
        Instant deliveredAt
) {}

