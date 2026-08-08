package com.marketplace.reward.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.reward.domain.port.CouponIssuer;
import com.marketplace.reward.domain.port.Fulfillment;
import org.junit.jupiter.api.Test;

class RewardStrategyTest {
    @Test
    void couponDelegatesToCouponIssuer() {
        CouponIssuer issuer = (reference, user, campaign) -> reference + "-LOCAL";
        assertThat(new CouponReward("C50", issuer).deliver("user", "campaign").deliveryReference())
                .isEqualTo("C50-LOCAL");
    }

    @Test
    void productDelegatesToFulfillment() {
        Fulfillment fulfillment = (reference, user, campaign) -> "SHIP-1";
        assertThat(new ProductReward("SKU-1", fulfillment).deliver("user", "campaign").deliveryReference())
                .isEqualTo("SHIP-1");
    }
}

