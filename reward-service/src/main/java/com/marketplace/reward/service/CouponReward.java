package com.marketplace.reward.service;

import com.marketplace.reward.domain.RewardResult;
import com.marketplace.reward.domain.RewardStrategy;
import com.marketplace.reward.domain.port.CouponIssuer;

public class CouponReward implements RewardStrategy {
    private final String reference;
    private final CouponIssuer issuer;

    public CouponReward(String reference, CouponIssuer issuer) {
        this.reference = reference;
        this.issuer = issuer;
    }

    @Override
    public RewardResult deliver(String userId, String campaignId) {
        return new RewardResult(issuer.issueCoupon(reference, userId, campaignId));
    }
}

