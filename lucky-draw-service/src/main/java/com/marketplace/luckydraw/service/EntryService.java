package com.marketplace.luckydraw.service;

import com.marketplace.events.EntrySubmitted;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.domain.Entry;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.QuotaRepository;
import com.marketplace.luckydraw.domain.port.TicketRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@Service
public class EntryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CampaignRepository campaigns;
    private final TicketRepository tickets;
    private final QuotaRepository quota;
    private final EntryRepository entries;
    private final OutboxRepository outbox;
    private final Clock clock;

    public EntryService(CampaignRepository campaigns, TicketRepository tickets, QuotaRepository quota,
            EntryRepository entries, OutboxRepository outbox, Clock clock) {
        this.campaigns = campaigns;
        this.tickets = tickets;
        this.quota = quota;
        this.entries = entries;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 5,
            backoff = @Backoff(delay = 10, multiplier = 2))
    public Entry submit(String userId, String campaignId, String ticketId, String correlationId) {
        var campaign = campaigns.lockShared(campaignId).orElseThrow(DomainException::notFound);
        if (!campaign.isOpenAt(clock.instant())) throw DomainException.campaignClosed();

        String entryId = UUID.randomUUID().toString();
        if (!tickets.consume(ticketId, userId, campaignId, entryId)) throw DomainException.ticketUnusable();
        if (!quota.tryReserve(campaignId, userId, campaign.maxEntriesPerUser())) throw DomainException.quotaReached();

        int wheelSegment = RANDOM.nextInt(8);
        boolean rewardPending = Entry.isRewardSegment(wheelSegment);
        var entry = entries.insert(entryId, campaignId, userId, ticketId, clock.instant(),
                wheelSegment, rewardPending);
        var eventId = UUID.randomUUID();
        var reward = new com.marketplace.events.Reward(
                com.marketplace.events.Reward.Type.valueOf(campaign.reward().type().name()),
                campaign.reward().reference());
        var event = new EntrySubmitted(eventId, clock.instant(), entry.id(), correlationId,
                entry.id(), campaignId, userId, ticketId, entry.sequence(), campaign.maxEntriesPerUser(),
                wheelSegment, rewardPending, reward);
        outbox.append(eventId.toString(), campaignId, "EntrySubmitted", event);
        return entry;
    }
}
