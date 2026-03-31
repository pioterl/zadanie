package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.PricingInfo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class MockPricingService implements PricingService {

    private static final double RELIABILITY = 99.5;
    private static final long BASE_LATENCY_MS = 80;

    private static final Map<String, String> MARKET_CURRENCIES = Map.of(
            "nl-NL", "EUR",
            "de-DE", "EUR",
            "pl-PL", "PLN",
            "fr-FR", "EUR",
            "gb-GB", "GBP"
    );

    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "PRD-001", new BigDecimal("349.99"),
            "PRD-002", new BigDecimal("24.50")
    );

    // PLN prices are higher to reflect exchange rate
    private static final Map<String, BigDecimal> PLN_PRICES = Map.of(
            "PRD-001", new BigDecimal("1549.99"),
            "PRD-002", new BigDecimal("109.50")
    );

    private static final Map<String, BigDecimal> CUSTOMER_DISCOUNTS = Map.of(
            "CUST-GOLD", new BigDecimal("0.15"),
            "CUST-SILVER", new BigDecimal("0.10"),
            "CUST-BRONZE", new BigDecimal("0.05")
    );

    @Override
    public PricingInfo getPricing(String productId, String market, String customerId) {
        MockLatencySimulator.simulateLatency("PricingService", BASE_LATENCY_MS);
        MockLatencySimulator.simulateFailure("PricingService", RELIABILITY);

        String currency = MARKET_CURRENCIES.getOrDefault(market, "EUR");

        BigDecimal basePrice;
        if ("PLN".equals(currency)) {
            basePrice = PLN_PRICES.getOrDefault(productId, new BigDecimal("99.99"));
        } else {
            basePrice = BASE_PRICES.getOrDefault(productId, new BigDecimal("99.99"));
        }

        BigDecimal discountRate = BigDecimal.ZERO;
        if (customerId != null) {
            discountRate = CUSTOMER_DISCOUNTS.getOrDefault(customerId, BigDecimal.ZERO);
        }

        BigDecimal discount = basePrice.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = basePrice.subtract(discount);

        return new PricingInfo(productId, currency, basePrice, discount, finalPrice);
    }
}
