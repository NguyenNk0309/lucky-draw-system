package com.marketplace.scheduler.domain.port;

import java.util.List;

public interface CampaignCloser {
    List<String> findDueCampaigns();
    void closeAndFreeze(String campaignId);
}

