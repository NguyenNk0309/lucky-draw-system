package com.marketplace.campaign.domain;

import java.time.Instant;

public record Campaign(
        String id,
        String sellerId,
        String name,
        Status status,
        int maxEntriesPerUser,
        Instant startAt,
        Instant endAt,
        Reward reward,
        String winnerEntryId,
        String snapshotHash) {
    public enum Status { DRAFT, ACTIVE, ENDED, DRAWN, CANCELLED }
}
