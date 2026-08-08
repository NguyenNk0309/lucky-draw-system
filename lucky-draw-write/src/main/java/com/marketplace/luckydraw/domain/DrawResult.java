package com.marketplace.luckydraw.domain;

public record DrawResult(Entry winner, String snapshotHash, long selectedIndex) {}

