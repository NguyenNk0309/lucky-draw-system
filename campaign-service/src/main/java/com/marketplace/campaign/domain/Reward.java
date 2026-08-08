package com.marketplace.campaign.domain;

public record Reward(Type type, String reference) {
    public enum Type { PRODUCT, COUPON }
}
