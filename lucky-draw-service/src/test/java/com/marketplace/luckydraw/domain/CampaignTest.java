package com.marketplace.luckydraw.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CampaignTest {
    private final Instant start = Instant.parse("2026-08-08T00:00:00Z");
    private final Instant end = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void onlyActiveCampaignInsideWindowIsOpen() {
        var campaign = campaign(CampaignStatus.ACTIVE);
        assertThat(campaign.isOpenAt(start)).isTrue();
        assertThat(campaign.isOpenAt(end)).isFalse();
        assertThat(campaign(CampaignStatus.DRAFT).isOpenAt(start.plusSeconds(1))).isFalse();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new Campaign("id", "seller", "name", CampaignStatus.DRAFT, 0,
                start, end, new Reward(Reward.Type.COUPON, "C50"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Campaign campaign(CampaignStatus status) {
        return new Campaign("id", "seller", "name", status, 2, start, end,
                new Reward(Reward.Type.COUPON, "C50"), null, null);
    }
}

