package com.marketplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.order.domain.port.OrderRepository;
import com.marketplace.order.domain.port.OutboxRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderServiceTest {
    private final OrderRepository orders = Mockito.mock(OrderRepository.class);
    private final OutboxRepository outbox = Mockito.mock(OutboxRepository.class);
    private final OrderService service = new OrderService(
            orders, outbox, Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void qualifyingOrderWritesOutboxEvent() {
        var order = service.create("customer-1", new BigDecimal("1000000.01"), "correlation");
        assertThat(order.userId()).isEqualTo("customer-1");
        verify(outbox).append(any());
    }

    @Test
    void thresholdItselfDoesNotQualify() {
        service.create("customer-1", new BigDecimal("1000000.00"), "correlation");
        verify(outbox, never()).append(any());
    }

    @Test
    void sellerCustomerDetailsSummarizeOrderHistory() {
        when(orders.findByUser("customer-1")).thenReturn(List.of(
                new com.marketplace.order.domain.Order("one", "customer-1", new BigDecimal("1200000"),
                        Instant.parse("2026-08-08T00:00:00Z")),
                new com.marketplace.order.domain.Order("two", "customer-1", new BigDecimal("300000"),
                        Instant.parse("2026-08-07T00:00:00Z"))));

        var details = service.customerDetails("customer-1");

        assertThat(details.totalOrders()).isEqualTo(2);
        assertThat(details.totalSpent()).isEqualByComparingTo("1500000");
    }
}
