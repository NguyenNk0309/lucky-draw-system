package com.marketplace.luckydraw.domain;

import java.time.Instant;

public record Ticket(
        String id,
        String orderId,
        String userId,
        Status status,
        String campaignId,
        String consumedByEntryId,
        Instant issuedAt,
        Instant consumedAt
) {
    public enum Status { ISSUED, CONSUMED }
}

