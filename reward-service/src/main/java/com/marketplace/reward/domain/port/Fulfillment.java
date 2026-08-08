package com.marketplace.reward.domain.port;

public interface Fulfillment {
    String reserveProduct(String productReference, String userId, String campaignId);
}

