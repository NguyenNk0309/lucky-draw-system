package com.marketplace.luckydraw.service;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignLifecycleService {
    private final CampaignRepository campaigns;
    private final EntryRepository entries;
    private final SnapshotRepository snapshots;
    private final OutboxRepository outbox;
    private final Clock clock;

    public CampaignLifecycleService(CampaignRepository campaigns, EntryRepository entries,
            SnapshotRepository snapshots, OutboxRepository outbox, Clock clock) {
        this.campaigns = campaigns; this.entries = entries; this.snapshots = snapshots;
        this.outbox = outbox; this.clock = clock;
    }

    public List<Campaign> list() { return campaigns.findAll(); }

    @Transactional
    public Campaign end(String id, String sellerId) {
        Campaign campaign = campaigns.lockExclusive(id).orElseThrow(DomainException::notFound);
        if (!campaign.sellerId().equals(sellerId)) throw DomainException.forbidden();
        if (campaign.status() == CampaignStatus.ACTIVE && !campaigns.end(id, sellerId)) {
            throw DomainException.invalidTransition();
        }
        if (campaign.status() != CampaignStatus.ACTIVE && campaign.status() != CampaignStatus.ENDED) {
            throw DomainException.invalidTransition();
        }
        var snapshot = snapshots.freeze(id);
        for (var winner : entries.findRewardPendingByCampaign(id)) {
            var eventId = UUID.randomUUID();
            var reward = new com.marketplace.events.Reward(
                    com.marketplace.events.Reward.Type.valueOf(campaign.reward().type().name()),
                    campaign.reward().reference());
            var event = new com.marketplace.events.WinnerPicked(eventId, clock.instant(), id,
                    UUID.randomUUID().toString(), id, winner.id(), winner.userId(), snapshot.contentHash(), reward);
            outbox.append(eventId.toString(), id, "WinnerPicked", event);
        }
        if (!campaigns.markDrawn(id, snapshot.contentHash())) throw DomainException.invalidTransition();
        Campaign drawn = campaigns.findById(id).orElseThrow(DomainException::notFound);
        publish(drawn);
        return drawn;
    }

    private void publish(Campaign campaign) {
        var id = UUID.randomUUID();
        var reward = new com.marketplace.events.Reward(
                com.marketplace.events.Reward.Type.valueOf(campaign.reward().type().name()),
                campaign.reward().reference());
        var event = new com.marketplace.events.CampaignUpdated(id, clock.instant(), campaign.id(),
                UUID.randomUUID().toString(), campaign.id(), campaign.sellerId(), campaign.name(),
                campaign.status().name(), campaign.maxEntriesPerUser(), campaign.startAt(), campaign.endAt(), reward);
        outbox.append(id.toString(), campaign.id(), "CampaignUpdated", event);
    }
}
