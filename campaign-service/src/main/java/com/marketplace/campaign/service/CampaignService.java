package com.marketplace.campaign.service;

import com.marketplace.campaign.domain.Campaign;
import com.marketplace.campaign.domain.Reward;
import com.marketplace.campaign.domain.port.CampaignRepository;
import com.marketplace.campaign.domain.port.OutboxRepository;
import com.marketplace.events.CampaignUpdated;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private final CampaignRepository campaigns;
    private final OutboxRepository outbox;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, OutboxRepository outbox, Clock clock) {
        this.campaigns = campaigns;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Campaign create(String sellerId, String name, Instant startAt, Instant endAt,
            int maxEntriesPerUser, Reward reward) {
        if (!startAt.isBefore(endAt)) throw new InvalidRequestException("Start must be before end");
        var campaign = campaigns.insert(new Campaign(UUID.randomUUID().toString(), sellerId, name,
                Campaign.Status.DRAFT, maxEntriesPerUser, startAt, endAt, reward, null, null));
        publish(campaign);
        return campaign;
    }

    public List<Campaign> list() { return campaigns.findAll(); }

    public Campaign get(String id) {
        return campaigns.findById(id).orElseThrow(() -> new NotFoundException("Campaign not found"));
    }

    @Transactional
    public Campaign activate(String id, String sellerId) {
        ensureOwner(id, sellerId);
        if (!campaigns.activate(id, sellerId)) throw new ConflictException("Campaign cannot be activated");
        var campaign = get(id);
        publish(campaign);
        return campaign;
    }

    @Transactional
    public Campaign cancel(String id, String sellerId) {
        ensureOwner(id, sellerId);
        if (!campaigns.cancel(id, sellerId)) throw new ConflictException("Campaign cannot be cancelled");
        var campaign = get(id);
        publish(campaign);
        return campaign;
    }

    private void ensureOwner(String id, String sellerId) {
        if (!get(id).sellerId().equals(sellerId)) throw new ForbiddenException("Campaign belongs to another seller");
    }

    private void publish(Campaign campaign) {
        var id = UUID.randomUUID();
        var reward = new com.marketplace.events.Reward(
                com.marketplace.events.Reward.Type.valueOf(campaign.reward().type().name()),
                campaign.reward().reference());
        var event = new CampaignUpdated(id, clock.instant(), campaign.id(), UUID.randomUUID().toString(),
                campaign.id(), campaign.sellerId(), campaign.name(), campaign.status().name(),
                campaign.maxEntriesPerUser(), campaign.startAt(), campaign.endAt(), reward);
        outbox.append(id.toString(), campaign.id(), "CampaignUpdated", event);
    }

    public static class InvalidRequestException extends RuntimeException { public InvalidRequestException(String m) { super(m); } }
    public static class NotFoundException extends RuntimeException { public NotFoundException(String m) { super(m); } }
    public static class ConflictException extends RuntimeException { public ConflictException(String m) { super(m); } }
    public static class ForbiddenException extends RuntimeException { public ForbiddenException(String m) { super(m); } }
}
