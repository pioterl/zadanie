package com.kramp.aggregator.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggregatedProductResponse(
        ProductInfo product,
        PricingInfo pricing,
        AvailabilityInfo availability,
        CustomerInfo customer,
        String market,
        DataStatus dataStatus
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DataStatus(
            boolean pricingAvailable,
            boolean availabilityAvailable,
            boolean customerAvailable,
            String pricingError,
            String availabilityError,
            String customerError
    ) {}
}
