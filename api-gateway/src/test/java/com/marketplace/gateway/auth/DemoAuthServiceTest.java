package com.marketplace.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DemoAuthServiceTest {
    @Test
    void signsAndRejectsTamperedTokens() {
        var auth = new DemoAuthService("secret", "customer123", "seller123",
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var login = auth.login("customer", "customer123");
        assertThat(auth.verify(login.token())).isEqualTo(new DemoAuthService.Identity("customer-1", "CUSTOMER"));
        assertThatThrownBy(() -> auth.verify(login.token() + "x")).isInstanceOf(DemoAuthService.UnauthorizedException.class);
    }
}
