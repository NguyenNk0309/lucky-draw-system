package com.marketplace.order.api;

import com.marketplace.order.domain.Order;
import com.marketplace.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(
            @RequestHeader("X-Demo-User") String userId,
            @RequestHeader(value = "X-Demo-Role", defaultValue = "CUSTOMER") String role,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CreateOrder request) {
        if (!"CUSTOMER".equals(role)) throw new ForbiddenException();
        return service.create(userId, request.total(), correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }

    public record CreateOrder(@NotNull @DecimalMin("0.01") BigDecimal total) {}

    @ResponseStatus(HttpStatus.FORBIDDEN)
    private static class ForbiddenException extends RuntimeException {}
}

