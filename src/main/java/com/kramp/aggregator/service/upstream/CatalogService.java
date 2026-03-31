package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.ProductInfo;

public interface CatalogService {
    ProductInfo getProduct(String productId, String market);
}
