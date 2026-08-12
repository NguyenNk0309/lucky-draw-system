package com.marketplace.luckydraw.domain.port;

import com.marketplace.luckydraw.domain.Entry;
import java.time.Instant;
import java.util.Optional;

public interface EntryRepository {
    Entry insert(String id, String campaignId, String userId, String ticketId, Instant submittedAt);
    Optional<Entry> findEntryById(String id);
    Entry findBySnapshotIndex(String campaignId, long index);
}
