package com.marketplace.order.api;

import com.marketplace.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final OrderService orders;

    public CustomerController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{userId}")
    public OrderService.CustomerDetails get(@PathVariable String userId,
            @RequestHeader("X-Demo-Role") String role) {
        if (!"SELLER".equals(role)) throw new ForbiddenException();
        return orders.customerDetails(userId);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    private static class ForbiddenException extends RuntimeException {}
}
