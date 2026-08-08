package com.marketplace.luckydraw.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.QuotaRepository;
import com.marketplace.luckydraw.domain.port.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntryServiceTest {
    private final CampaignRepository campaigns = Mockito.mock(CampaignRepository.class);
    private final TicketRepository tickets = Mockito.mock(TicketRepository.class);
    private final QuotaRepository quota = Mockito.mock(QuotaRepository.class);
    private final EntryRepository entries = Mockito.mock(EntryRepository.class);
    private final OutboxRepository outbox = Mockito.mock(OutboxRepository.class);
    private final Instant now = Instant.parse("2026-08-08T12:00:00Z");
    private final EntryService service = new EntryService(campaigns, tickets, quota, entries, outbox,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void quotaFailureStopsBeforeEntryAndRollsBackAtTransactionBoundary() {
        when(campaigns.lockShared("campaign")).thenReturn(Optional.of(openCampaign()));
        when(tickets.consume(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true);
        when(quota.tryReserve("campaign", "customer", 2)).thenReturn(false);

        assertThatThrownBy(() -> service.submit("customer", "campaign", "ticket", "correlation"))
                .isInstanceOf(DomainException.class)
                .hasMessage("Entry quota reached");
        verify(entries, never()).insert(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any());
    }

    private Campaign openCampaign() {
        return new Campaign("campaign", "seller", "Campaign", CampaignStatus.ACTIVE, 2,
                now.minusSeconds(60), now.plusSeconds(60), new Reward(Reward.Type.COUPON, "C50"), null, null);
    }
}

