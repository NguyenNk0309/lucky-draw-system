package com.marketplace.luckydraw.domain;

import java.time.Instant;

public record Entry(
        String id,
        String campaignId,
        String userId,
        String ticketId,
        long sequence,
        Instant submittedAt
) {}
