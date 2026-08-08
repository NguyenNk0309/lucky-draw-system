package com.marketplace.reward.service;

import com.marketplace.events.Reward;
import com.marketplace.reward.domain.RewardStrategy;
import com.marketplace.reward.domain.port.CouponIssuer;
import com.marketplace.reward.domain.port.Fulfillment;
import org.springframework.stereotype.Component;

@Component
public class RewardStrategyFactory {
    private final Fulfillment fulfillment;
    private final CouponIssuer coupons;

    public RewardStrategyFactory(Fulfillment fulfillment, CouponIssuer coupons) {
        this.fulfillment = fulfillment;
        this.coupons = coupons;
    }

    public RewardStrategy forReward(Reward reward) {
        return switch (reward.type()) {
            case PRODUCT -> new ProductReward(reward.reference(), fulfillment);
            case COUPON -> new CouponReward(reward.reference(), coupons);
        };
    }
}

