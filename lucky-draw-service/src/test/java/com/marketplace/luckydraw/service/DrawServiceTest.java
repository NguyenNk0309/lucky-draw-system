package com.marketplace.luckydraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.events.WinnerPicked;
import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DrawSnapshot;
import com.marketplace.luckydraw.domain.Entry;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DrawServiceTest {
    @Test
    void sellerDrawsExactlyOneWinnerFromFrozenSnapshot() {
        var now = Instant.parse("2026-08-09T12:00:00Z");
        var campaigns = mock(CampaignRepository.class);
        var entries = mock(EntryRepository.class);
        var snapshots = mock(SnapshotRepository.class);
        var outbox = mock(OutboxRepository.class);
        var campaign = new Campaign("campaign", "seller", "Campaign", CampaignStatus.ENDED, 3,
                now.minusSeconds(60), now, new Reward(Reward.Type.COUPON, "SAVE-50"), null, null);
        var winner = new Entry("entry", "campaign", "customer", "ticket", 1, now.minusSeconds(10));
        when(campaigns.lockExclusive("campaign")).thenReturn(Optional.of(campaign));
        when(snapshots.find("campaign")).thenReturn(Optional.of(new DrawSnapshot("campaign", 1, "hash", now)));
        when(entries.findBySnapshotIndex("campaign", 1)).thenReturn(winner);
        when(campaigns.markDrawn("campaign", "entry", "hash")).thenReturn(true);

        var result = new DrawService(campaigns, entries, snapshots, outbox,
                Clock.fixed(now, ZoneOffset.UTC)).draw("campaign", "seller", "correlation");

        assertThat(result.winner()).isEqualTo(winner);
        verify(snapshots).recordAudit("campaign", 1, "entry", "hash");
        verify(outbox).append(anyString(), eq("campaign"), eq("WinnerPicked"), any(WinnerPicked.class));
    }
}
