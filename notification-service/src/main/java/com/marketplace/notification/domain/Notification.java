package com.marketplace.notification.domain;

import java.time.Instant;

public record Notification(String id, String campaignId, String userId, String message, Instant sentAt) {}

