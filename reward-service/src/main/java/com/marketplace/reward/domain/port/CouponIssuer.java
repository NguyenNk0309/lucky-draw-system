package com.marketplace.reward.domain.port;

public interface CouponIssuer {
    String issueCoupon(String couponReference, String userId, String campaignId);
}

