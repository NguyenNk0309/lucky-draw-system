package com.marketplace.luckydraw.domain.port;

import com.marketplace.luckydraw.domain.Campaign;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository {
    List<Campaign> findAll();
    Optional<Campaign> findById(String id);
    Optional<Campaign> lockShared(String id);
    Optional<Campaign> lockExclusive(String id);
    boolean end(String id, String sellerId);
    boolean recordSnapshotHash(String id, String snapshotHash);
    boolean markDrawn(String id, String winnerEntryId, String snapshotHash);
}
