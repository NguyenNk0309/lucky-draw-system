package com.marketplace.luckydraw.domain.port;

import com.marketplace.luckydraw.domain.Entry;
import java.time.Instant;
import java.util.List;

public interface EntryRepository {
    Entry insert(String id, String campaignId, String userId, String ticketId, Instant submittedAt,
            int wheelSegment, boolean rewardPending);
    List<Entry> findRewardPendingByCampaign(String campaignId);
    boolean cancelReward(String campaignId, String entryId, Instant canceledAt);
}
