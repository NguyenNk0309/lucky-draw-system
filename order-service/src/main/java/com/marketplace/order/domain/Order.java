package com.marketplace.order.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(String id, String userId, BigDecimal total, Instant createdAt) {}

