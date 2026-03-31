package com.kramp.aggregator.model;

import java.util.List;

public record ProductInfo(
        String productId,
        String name,
        String description,
        String category,
        List<String> imageUrls,
        List<Spec> specs
) {
    public record Spec(String name, String value, String unit) {}
}
