package com.marketplace.luckydraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.events.RewardCanceled;
import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.Entry;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampaignLifecycleServiceTest {
    @Test
    void sellerCancelsSeveralPendingRewardsInOneTransaction() {
        var now = Instant.parse("2026-08-09T12:00:00Z");
        var campaigns = mock(CampaignRepository.class);
        var entries = mock(EntryRepository.class);
        var snapshots = mock(SnapshotRepository.class);
        var outbox = mock(OutboxRepository.class);
        var campaign = new Campaign("campaign", "seller", "Campaign", CampaignStatus.ACTIVE, 3,
                now.minusSeconds(60), now.plusSeconds(60),
                new Reward(Reward.Type.COUPON, "SAVE-50"), null, null);
        var first = entry("entry-1", 1, now);
        var second = entry("entry-2", 2, now);
        when(campaigns.lockExclusive("campaign")).thenReturn(Optional.of(campaign));
        when(entries.findRewardPendingByCampaign("campaign")).thenReturn(List.of(first, second));
        when(entries.cancelReward(eq("campaign"), anyString(), eq(now))).thenReturn(true);

        var service = new CampaignLifecycleService(campaigns, entries, snapshots, outbox,
                Clock.fixed(now, ZoneOffset.UTC));
        var canceled = service.cancelRewards("campaign", "seller", List.of("entry-1", "entry-2"), "request");

        assertThat(canceled).containsExactly(first, second);
        verify(entries).cancelReward("campaign", "entry-1", now);
        verify(entries).cancelReward("campaign", "entry-2", now);
        verify(outbox, times(2)).append(anyString(), eq("campaign"), eq("RewardCanceled"),
                any(RewardCanceled.class));
    }

    private static Entry entry(String id, long sequence, Instant now) {
        return new Entry(id, "campaign", "customer", "ticket-" + sequence, sequence, now,
                1, true, false, null);
    }
}
