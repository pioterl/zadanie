package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.AvailabilityInfo;

public interface AvailabilityService {
    AvailabilityInfo getAvailability(String productId, String market);
}
