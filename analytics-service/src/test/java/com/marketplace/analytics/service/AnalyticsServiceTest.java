package com.marketplace.analytics.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.marketplace.analytics.domain.port.ReadModelRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalyticsServiceTest {
    private final ReadModelRepository repository = Mockito.mock(ReadModelRepository.class);
    private final AnalyticsService service = new AnalyticsService(repository);

    @Test
    void sellerCannotReadAnotherSellersStats() {
        when(repository.sellerId("campaign")).thenReturn(Optional.of("seller-1"));
        assertThatThrownBy(() -> service.stats("campaign", "seller-2"))
                .isInstanceOf(AnalyticsService.ForbiddenException.class);
    }
}

