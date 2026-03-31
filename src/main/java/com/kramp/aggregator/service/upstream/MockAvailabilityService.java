package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.AvailabilityInfo;
import com.kramp.aggregator.model.AvailabilityInfo.StockStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MockAvailabilityService implements AvailabilityService {

    private static final double RELIABILITY = 98.0;
    private static final long BASE_LATENCY_MS = 100;

    private static final Map<String, String> MARKET_WAREHOUSES = Map.of(
            "nl-NL", "Rotterdam, NL",
            "de-DE", "Hamburg, DE",
            "pl-PL", "Poznań, PL",
            "fr-FR", "Lyon, FR"
    );

    @Override
    public AvailabilityInfo getAvailability(String productId, String market) {
        MockLatencySimulator.simulateLatency("AvailabilityService", BASE_LATENCY_MS);
        MockLatencySimulator.simulateFailure("AvailabilityService", RELIABILITY);

        String warehouse = MARKET_WAREHOUSES.getOrDefault(market, "Central EU Warehouse");

        // Simulate varying stock levels
        int stock = switch (productId) {
            case "PRD-001" -> ThreadLocalRandom.current().nextInt(0, 50);
            case "PRD-002" -> ThreadLocalRandom.current().nextInt(5, 200);
            default -> 0;
        };

        StockStatus status;
        String delivery;
        if (stock == 0) {
            status = StockStatus.OUT_OF_STOCK;
            delivery = "2-3 weeks";
        } else if (stock < 10) {
            status = StockStatus.LOW_STOCK;
            delivery = "1-2 business days";
        } else {
            status = StockStatus.IN_STOCK;
            delivery = "Next business day";
        }

        return new AvailabilityInfo(productId, stock, warehouse, delivery, status);
    }
}
