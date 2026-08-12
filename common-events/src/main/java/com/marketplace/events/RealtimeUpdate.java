package com.marketplace.events;

import java.time.Instant;
import java.util.UUID;

public record RealtimeUpdate(UUID eventId, Instant occurredAt, String userId, Type type) {
    public enum Type { NOTIFICATION, REWARD }
}
