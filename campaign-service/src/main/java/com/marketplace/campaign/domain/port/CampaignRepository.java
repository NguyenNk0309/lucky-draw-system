package com.marketplace.campaign.domain.port;

import com.marketplace.campaign.domain.Campaign;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository {
    Campaign insert(Campaign campaign);
    List<Campaign> findAll();
    Optional<Campaign> findById(String id);
    boolean activate(String id, String sellerId);
    boolean cancel(String id, String sellerId);
}
