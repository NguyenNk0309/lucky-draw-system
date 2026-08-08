package com.marketplace.order.domain.port;

import com.marketplace.events.OrderCompleted;

public interface OutboxRepository {
    void append(OrderCompleted event);
}

