package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.CustomerInfo;

public interface CustomerService {
    CustomerInfo getCustomer(String customerId);
}
