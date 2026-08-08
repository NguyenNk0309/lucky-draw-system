package com.marketplace.campaign.domain.port;

public interface OutboxRepository {
    void append(String id, String aggregateId, String eventType, Object event);
}
