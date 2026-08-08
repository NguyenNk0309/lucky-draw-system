package com.marketplace.order.domain.port;

import com.marketplace.order.domain.Order;
import java.util.List;

public interface OrderRepository {
    void insert(Order order);
    List<Order> findByUser(String userId);
}

