package com.marketplace.reward.service;

import com.marketplace.reward.domain.RewardResult;
import com.marketplace.reward.domain.RewardStrategy;
import com.marketplace.reward.domain.port.Fulfillment;

public class ProductReward implements RewardStrategy {
    private final String reference;
    private final Fulfillment fulfillment;

    public ProductReward(String reference, Fulfillment fulfillment) {
        this.reference = reference;
        this.fulfillment = fulfillment;
    }

    @Override
    public RewardResult deliver(String userId, String campaignId) {
        return new RewardResult(fulfillment.reserveProduct(reference, userId, campaignId));
    }
}

