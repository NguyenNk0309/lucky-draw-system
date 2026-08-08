package com.marketplace.luckydraw.domain;

import java.time.Instant;

public record Campaign(
        String id,
        String sellerId,
        String name,
        CampaignStatus status,
        int maxEntriesPerUser,
        Instant startAt,
        Instant endAt,
        Reward reward,
        String winnerEntryId,
        String snapshotHash
) {
    public Campaign {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Campaign name is required");
        if (maxEntriesPerUser < 1) throw new IllegalArgumentException("Entry limit must be positive");
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt must be before endAt");
        }
        if (reward == null || reward.reference() == null || reward.reference().isBlank()) {
            throw new IllegalArgumentException("Reward is required");
        }
    }

    public boolean isOpenAt(Instant now) {
        return status == CampaignStatus.ACTIVE && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public boolean canDraw() {
        return status == CampaignStatus.ENDED;
    }
}

