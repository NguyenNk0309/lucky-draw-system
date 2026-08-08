package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.DomainException;

final class DemoAuth {
    private DemoAuth() {}

    static void require(String actual, String expected) {
        if (!expected.equals(actual)) throw DomainException.forbidden();
    }
}

