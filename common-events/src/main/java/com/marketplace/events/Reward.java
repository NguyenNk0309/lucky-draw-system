package com.marketplace.events;

public record Reward(Type type, String reference) {
    public enum Type { PRODUCT, COUPON }
}

