package com.marketplace.luckydraw.service;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private final CampaignRepository campaigns;
    private final SnapshotRepository snapshots;
    private final OutboxRepository outbox;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, SnapshotRepository snapshots,
            OutboxRepository outbox, Clock clock) {
        this.campaigns = campaigns;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Campaign create(String sellerId, String name, Instant startAt, Instant endAt,
            int maxEntriesPerUser, Reward reward) {
        var campaign = campaigns.insert(new Campaign(UUID.randomUUID().toString(), sellerId, name, CampaignStatus.DRAFT,
                maxEntriesPerUser, startAt, endAt, reward, null, null));
        publish(campaign);
        return campaign;
    }

    public List<Campaign> list() {
        return campaigns.findAll();
    }

    public Campaign get(String id) {
        return campaigns.findById(id).orElseThrow(DomainException::notFound);
    }

    @Transactional
    public Campaign activate(String id, String sellerId) {
        ensureOwner(id, sellerId);
        if (!campaigns.activate(id, sellerId)) throw DomainException.invalidTransition();
        Campaign campaign = get(id);
        publish(campaign);
        return campaign;
    }

    @Transactional
    public Campaign cancel(String id, String sellerId) {
        ensureOwner(id, sellerId);
        if (!campaigns.cancel(id, sellerId)) throw DomainException.invalidTransition();
        Campaign campaign = get(id);
        publish(campaign);
        return campaign;
    }

    @Transactional
    public Campaign end(String id, String sellerId) {
        Campaign campaign = campaigns.lockExclusive(id).orElseThrow(DomainException::notFound);
        if (!campaign.sellerId().equals(sellerId)) throw DomainException.forbidden();
        if (!campaigns.end(id, sellerId)) throw DomainException.invalidTransition();
        snapshots.freeze(id);
        Campaign ended = get(id);
        publish(ended);
        return ended;
    }

    private void ensureOwner(String id, String sellerId) {
        if (!get(id).sellerId().equals(sellerId)) throw DomainException.forbidden();
    }

    private void publish(Campaign campaign) {
        var eventId = UUID.randomUUID();
        var reward = new com.marketplace.events.Reward(
                com.marketplace.events.Reward.Type.valueOf(campaign.reward().type().name()),
                campaign.reward().reference());
        var event = new com.marketplace.events.CampaignUpdated(eventId, clock.instant(), campaign.id(),
                UUID.randomUUID().toString(), campaign.id(), campaign.sellerId(), campaign.name(),
                campaign.status().name(), campaign.maxEntriesPerUser(), campaign.startAt(), campaign.endAt(), reward);
        outbox.append(eventId.toString(), campaign.id(), "CampaignUpdated", event);
    }
}
