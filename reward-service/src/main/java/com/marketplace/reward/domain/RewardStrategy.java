package com.marketplace.reward.domain;

public interface RewardStrategy {
    RewardResult deliver(String userId, String campaignId);
}

