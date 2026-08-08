package com.marketplace.luckydraw.service;

import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DomainException;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CampaignScheduler {
    private final CampaignLifecycleService campaigns;
    private final Clock clock;
    public CampaignScheduler(CampaignLifecycleService campaigns, Clock clock) { this.campaigns = campaigns; this.clock = clock; }

    @Scheduled(fixedDelayString = "${campaign.poll-ms:1000}")
    public void closeDueCampaigns() {
        // ponytail: scans campaigns; replace with an indexed due query when campaign volume warrants it.
        campaigns.list().stream()
                .filter(campaign -> campaign.status() == CampaignStatus.ACTIVE && !campaign.endAt().isAfter(clock.instant()))
                .forEach(campaign -> {
                    try { campaigns.end(campaign.id(), campaign.sellerId()); }
                    catch (DomainException ignored) { /* another scheduler or seller closed it */ }
                });
    }
}
