package com.marketplace.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCompleted(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String orderId,
        String userId,
        BigDecimal total
) {}

