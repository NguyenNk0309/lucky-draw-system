package com.marketplace.order.infrastructure.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.events.OrderCompleted;
import com.marketplace.order.domain.Order;
import com.marketplace.order.domain.port.OrderRepository;
import com.marketplace.order.domain.port.OutboxRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository, OutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcOrderRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(Order order) {
        jdbc.update("INSERT INTO orders (id, user_id, total, created_at) VALUES (?, ?, ?, ?)",
                order.id(), order.userId(), order.total(), order.createdAt());
    }

    @Override
    public void append(OrderCompleted event) {
        try {
            jdbc.update("INSERT INTO order_outbox (id, aggregate_id, event_type, payload) VALUES (?, ?, ?, ?)",
                    event.eventId().toString(), event.aggregateId(), "OrderCompleted", json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize order event", e);
        }
    }
}

