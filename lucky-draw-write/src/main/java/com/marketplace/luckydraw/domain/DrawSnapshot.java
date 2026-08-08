package com.marketplace.luckydraw.domain;

import java.time.Instant;

public record DrawSnapshot(String campaignId, long totalEntries, String contentHash, Instant frozenAt) {}

