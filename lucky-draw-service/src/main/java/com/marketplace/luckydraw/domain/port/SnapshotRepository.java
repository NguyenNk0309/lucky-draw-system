package com.marketplace.luckydraw.domain.port;

import com.marketplace.luckydraw.domain.DrawSnapshot;

public interface SnapshotRepository {
    DrawSnapshot freeze(String campaignId);
}
