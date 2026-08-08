package com.marketplace.campaign.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.campaign.domain.Reward;
import com.marketplace.campaign.domain.port.CampaignRepository;
import com.marketplace.campaign.domain.port.OutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CampaignServiceTest {
    @Test
    void rejectsInvalidWindowBeforeWriting() {
        var service = new CampaignService(Mockito.mock(CampaignRepository.class), Mockito.mock(OutboxRepository.class),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var time = Instant.parse("2026-01-02T00:00:00Z");
        assertThatThrownBy(() -> service.create("seller", "draw", time, time, 2,
                new Reward(Reward.Type.COUPON, "C50")))
                .isInstanceOf(CampaignService.InvalidRequestException.class);
    }
}
