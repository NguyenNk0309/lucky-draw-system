package com.marketplace.order.domain.port;

import com.marketplace.order.domain.Order;

public interface OrderRepository {
    void insert(Order order);
}

