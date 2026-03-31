package com.kramp.aggregator.model;

import java.math.BigDecimal;

public record PricingInfo(
        String productId,
        String currency,
        BigDecimal basePrice,
        BigDecimal discount,
        BigDecimal finalPrice
) {}
