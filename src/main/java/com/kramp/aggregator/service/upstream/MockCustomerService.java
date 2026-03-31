package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.CustomerInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MockCustomerService implements CustomerService {

    private static final double RELIABILITY = 99.0;
    private static final long BASE_LATENCY_MS = 60;

    private static final Map<String, CustomerInfo> CUSTOMERS = Map.of(
            "CUST-GOLD", new CustomerInfo("CUST-GOLD", "Gold Dealer",
                    List.of("hydraulics", "heavy-machinery", "bulk-orders")),
            "CUST-SILVER", new CustomerInfo("CUST-SILVER", "Silver Workshop",
                    List.of("filters", "maintenance-parts")),
            "CUST-BRONZE", new CustomerInfo("CUST-BRONZE", "Bronze Retailer",
                    List.of("general-parts"))
    );

    @Override
    public CustomerInfo getCustomer(String customerId) {
        MockLatencySimulator.simulateLatency("CustomerService", BASE_LATENCY_MS);
        MockLatencySimulator.simulateFailure("CustomerService", RELIABILITY);

        CustomerInfo customer = CUSTOMERS.get(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        return customer;
    }
}
