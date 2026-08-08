package com.marketplace.luckydraw.domain.port;

public interface OutboxRepository {
    void append(String eventId, String aggregateId, String eventType, Object event);
}

