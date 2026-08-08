package com.marketplace.analytics.service;

import com.marketplace.analytics.domain.CampaignStats;
import com.marketplace.analytics.domain.MyResult;
import com.marketplace.analytics.domain.port.ReadModelRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final ReadModelRepository readModel;

    public AnalyticsService(ReadModelRepository readModel) {
        this.readModel = readModel;
    }

    public CampaignStats stats(String campaignId, String sellerId) {
        String owner = readModel.sellerId(campaignId).orElseThrow(NotFoundException::new);
        if (!owner.equals(sellerId)) throw new ForbiddenException();
        return readModel.stats(campaignId);
    }

    public MyResult mine(String campaignId, String userId) {
        return readModel.mine(campaignId, userId);
    }

    public static class NotFoundException extends RuntimeException {}
    public static class ForbiddenException extends RuntimeException {}
}

