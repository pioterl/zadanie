package com.kramp.aggregator.model;

public record AvailabilityInfo(
        String productId,
        int stockLevel,
        String warehouseLocation,
        String expectedDelivery,
        StockStatus status
) {
    public enum StockStatus {
        IN_STOCK, LOW_STOCK, OUT_OF_STOCK
    }
}
