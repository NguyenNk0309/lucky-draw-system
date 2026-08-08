package com.marketplace.luckydraw.service;

import com.marketplace.events.WinnerPicked;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.domain.DrawResult;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrawService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CampaignRepository campaigns;
    private final EntryRepository entries;
    private final SnapshotRepository snapshots;
    private final OutboxRepository outbox;
    private final Clock clock;

    public DrawService(CampaignRepository campaigns, EntryRepository entries, SnapshotRepository snapshots,
            OutboxRepository outbox, Clock clock) {
        this.campaigns = campaigns;
        this.entries = entries;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public DrawResult draw(String campaignId, String sellerId, String correlationId) {
        var campaign = campaigns.lockExclusive(campaignId).orElseThrow(DomainException::notFound);
        if (!campaign.sellerId().equals(sellerId)) throw DomainException.forbidden();
        if (campaign.status() == CampaignStatus.DRAWN) {
            var winner = entries.findEntryById(campaign.winnerEntryId()).orElseThrow();
            return new DrawResult(winner, campaign.snapshotHash(), snapshots.selectedIndex(campaignId));
        }
        if (!campaign.canDraw()) throw DomainException.notDrawable();

        var snapshot = snapshots.find(campaignId).orElseGet(() -> snapshots.freeze(campaignId));
        if (snapshot.totalEntries() == 0) throw DomainException.noEntries();
        long selectedIndex = RANDOM.nextLong(snapshot.totalEntries()) + 1;
        var winner = entries.findBySnapshotIndex(campaignId, selectedIndex);
        if (!campaigns.markDrawn(campaignId, winner.id(), snapshot.contentHash())) {
            throw DomainException.notDrawable();
        }
        snapshots.recordAudit(campaignId, selectedIndex, winner.id(), snapshot.contentHash());

        var eventId = UUID.randomUUID();
        var reward = new com.marketplace.events.Reward(
                com.marketplace.events.Reward.Type.valueOf(campaign.reward().type().name()),
                campaign.reward().reference());
        var event = new WinnerPicked(eventId, clock.instant(), campaignId, correlationId, campaignId,
                winner.id(), winner.userId(), snapshot.contentHash(), reward);
        outbox.append(eventId.toString(), campaignId, "WinnerPicked", event);
        return new DrawResult(winner, snapshot.contentHash(), selectedIndex);
    }
}
