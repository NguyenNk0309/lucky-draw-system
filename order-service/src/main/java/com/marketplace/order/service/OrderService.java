package com.marketplace.order.service;

import com.marketplace.events.OrderCompleted;
import com.marketplace.order.domain.Order;
import com.marketplace.order.domain.port.OrderRepository;
import com.marketplace.order.domain.port.OutboxRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    static final BigDecimal QUALIFYING_TOTAL = new BigDecimal("1000000.00");

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final Clock clock;

    public OrderService(OrderRepository orders, OutboxRepository outbox, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Order create(String userId, BigDecimal total, String correlationId) {
        var now = clock.instant();
        var order = new Order(UUID.randomUUID().toString(), userId, total, now);
        orders.insert(order);
        if (total.compareTo(QUALIFYING_TOTAL) > 0) {
            outbox.append(new OrderCompleted(
                    UUID.randomUUID(), now, order.id(), correlationId, order.id(), userId, total));
        }
        return order;
    }
}
