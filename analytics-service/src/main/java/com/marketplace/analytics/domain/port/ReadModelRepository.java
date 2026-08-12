package com.marketplace.analytics.domain.port;

import com.marketplace.analytics.domain.CampaignStats;
import com.marketplace.analytics.domain.MyResult;
import com.marketplace.events.CampaignUpdated;
import com.marketplace.events.EntrySubmitted;
import com.marketplace.events.WinnerPicked;
import java.util.Optional;

public interface ReadModelRepository {
    boolean project(CampaignUpdated event);
    boolean project(EntrySubmitted event);
    boolean project(WinnerPicked event);
    Optional<String> sellerId(String campaignId);
    CampaignStats stats(String campaignId);
    MyResult mine(String campaignId, String userId);
}
