package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.PricingInfo;

public interface PricingService {
    PricingInfo getPricing(String productId, String market, String customerId);
}
