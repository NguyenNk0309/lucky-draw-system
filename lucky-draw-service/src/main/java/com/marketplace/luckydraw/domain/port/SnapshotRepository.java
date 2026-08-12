package com.marketplace.luckydraw.domain.port;

import com.marketplace.luckydraw.domain.DrawSnapshot;
import java.time.Instant;
import java.util.Optional;

public interface SnapshotRepository {
    DrawSnapshot freeze(String campaignId, Instant frozenAt);
    Optional<DrawSnapshot> find(String campaignId);
    void recordAudit(String campaignId, long selectedIndex, String winnerEntryId, String snapshotHash);
    long selectedIndex(String campaignId);
}
