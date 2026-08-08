package com.marketplace.luckydraw.service;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private final CampaignRepository campaigns;
    private final SnapshotRepository snapshots;

    public CampaignService(CampaignRepository campaigns, SnapshotRepository snapshots) {
        this.campaigns = campaigns;
        this.snapshots = snapshots;
    }

    @Transactional
    public Campaign create(String sellerId, String name, Instant startAt, Instant endAt,
            int maxEntriesPerUser, Reward reward) {
        return campaigns.insert(new Campaign(UUID.randomUUID().toString(), sellerId, name, CampaignStatus.DRAFT,
                maxEntriesPerUser, startAt, endAt, reward, null, null));
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
        return get(id);
    }

    @Transactional
    public Campaign cancel(String id, String sellerId) {
        ensureOwner(id, sellerId);
        if (!campaigns.cancel(id, sellerId)) throw DomainException.invalidTransition();
        return get(id);
    }

    @Transactional
    public Campaign end(String id, String sellerId) {
        Campaign campaign = campaigns.lockExclusive(id).orElseThrow(DomainException::notFound);
        if (!campaign.sellerId().equals(sellerId)) throw DomainException.forbidden();
        if (!campaigns.end(id, sellerId)) throw DomainException.invalidTransition();
        snapshots.freeze(id);
        return get(id);
    }

    private void ensureOwner(String id, String sellerId) {
        if (!get(id).sellerId().equals(sellerId)) throw DomainException.forbidden();
    }
}

