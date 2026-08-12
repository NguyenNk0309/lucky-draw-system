package com.marketplace.luckydraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DrawSnapshot;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampaignLifecycleServiceTest {
    @Test
    void endingCampaignFreezesSnapshotWithoutPickingWinner() {
        var now = Instant.parse("2026-08-09T12:00:00Z");
        var campaigns = mock(CampaignRepository.class);
        var snapshots = mock(SnapshotRepository.class);
        var outbox = mock(OutboxRepository.class);
        var active = campaign(CampaignStatus.ACTIVE, now);
        var ended = campaign(CampaignStatus.ENDED, now);
        when(campaigns.lockExclusive("campaign")).thenReturn(Optional.of(active));
        when(campaigns.end("campaign", "seller")).thenReturn(true);
        when(snapshots.freeze("campaign", now)).thenReturn(new DrawSnapshot("campaign", 0, "hash", now));
        when(campaigns.recordSnapshotHash("campaign", "hash")).thenReturn(true);
        when(campaigns.findById("campaign")).thenReturn(Optional.of(ended));

        var service = new CampaignLifecycleService(campaigns, snapshots, outbox,
                Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.end("campaign", "seller").status()).isEqualTo(CampaignStatus.ENDED);
        verify(snapshots).freeze("campaign", now);
        verify(campaigns).recordSnapshotHash("campaign", "hash");
        verify(outbox).append(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("campaign"),
                org.mockito.ArgumentMatchers.eq("CampaignUpdated"), org.mockito.ArgumentMatchers.any());
    }

    private static Campaign campaign(CampaignStatus status, Instant now) {
        return new Campaign("campaign", "seller", "Campaign", status, 3,
                now.minusSeconds(60), now.plusSeconds(60),
                new Reward(Reward.Type.COUPON, "SAVE-50"), null, null);
    }
}
