package com.marketplace.scheduler.service;

import com.marketplace.scheduler.domain.port.CampaignCloser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CampaignScheduler {
    private final CampaignCloser campaigns;

    public CampaignScheduler(CampaignCloser campaigns) {
        this.campaigns = campaigns;
    }

    @Scheduled(fixedDelayString = "${campaign.poll-ms:1000}")
    public void closeDueCampaigns() {
        campaigns.findDueCampaigns().forEach(campaigns::closeAndFreeze);
    }
}

