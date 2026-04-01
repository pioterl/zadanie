package com.kramp.aggregator.service;

import com.kramp.aggregator.exception.CatalogUnavailableException;
import com.kramp.aggregator.exception.ProductNotFoundException;
import com.kramp.aggregator.model.*;
import com.kramp.aggregator.model.AggregatedProductResponse.DataStatus;
import com.kramp.aggregator.service.upstream.AvailabilityService;
import com.kramp.aggregator.service.upstream.CatalogService;
import com.kramp.aggregator.service.upstream.CustomerService;
import com.kramp.aggregator.service.upstream.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class ProductAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(ProductAggregatorService.class);

    private final CatalogService catalogService;
    private final PricingService pricingService;
    private final AvailabilityService availabilityService;
    private final CustomerService customerService;
    private final ExecutorService executor;
    private final long timeoutMs;

    public ProductAggregatorService(
            CatalogService catalogService,
            PricingService pricingService,
            AvailabilityService availabilityService,
            CustomerService customerService,
            @Value("${aggregator.timeout-ms:500}") long timeoutMs
    ) {
        this.catalogService = catalogService;
        this.pricingService = pricingService;
        this.availabilityService = availabilityService;
        this.customerService = customerService;
        this.timeoutMs = timeoutMs;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public AggregatedProductResponse getProduct(String productId, String market, String customerId) {
        log.info("Aggregating product info: productId={}, market={}, customerId={}", productId, market, customerId);

        // 1. Dispatch all upstream calls in parallel
        CompletableFuture<ProductInfo> catalogFuture = supplyAsync(
                () -> catalogService.getProduct(productId, market));
        CompletableFuture<PricingInfo> pricingFuture = supplyAsync(
                () -> pricingService.getPricing(productId, market, customerId));
        CompletableFuture<AvailabilityInfo> availabilityFuture = supplyAsync(
                () -> availabilityService.getAvailability(productId, market));
        CompletableFuture<CustomerInfo> customerFuture = customerId != null ? supplyAsync(
                () -> customerService.getCustomer(customerId)) : CompletableFuture.completedFuture(null);

        // 2. Collect results — catalog is required, the rest degrade gracefully
        ProductInfo product = fetchRequired(catalogFuture, productId);
        PricingInfo pricing = fetchOptional(pricingFuture, "PricingService");
        AvailabilityInfo availability = fetchOptional(availabilityFuture, "AvailabilityService");
        CustomerInfo customer = fetchOptional(customerFuture, "CustomerService");

        // 3. Build response with data status for the frontend
        DataStatus dataStatus = buildDataStatus(pricing, availability, customer, customerId);
        return new AggregatedProductResponse(product, pricing, availability, customer, market, dataStatus);
    }

    // --- Upstream call helpers ---

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private ProductInfo fetchRequired(CompletableFuture<ProductInfo> future, String productId) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw new ProductNotFoundException("Product not found: " + productId, e.getCause());
            }
            throw new CatalogUnavailableException("Catalog service failed for product: " + productId, e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new CatalogUnavailableException("Catalog service timed out for product: " + productId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException("Catalog request interrupted", e);
        }
    }

    private <T> T fetchOptional(CompletableFuture<T> future, String serviceName) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("{} timed out — returning degraded response", serviceName);
            future.cancel(true);
            return null;
        } catch (ExecutionException e) {
            log.warn("{} failed — returning degraded response: {}", serviceName, e.getCause().getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} interrupted", serviceName);
            return null;
        }
    }

    // --- Response building ---

    private DataStatus buildDataStatus(PricingInfo pricing, AvailabilityInfo availability, CustomerInfo customer, String customerId) {
        boolean customerRequested = customerId != null;

        return new DataStatus(
                /* pricingAvailable */       pricing != null,
                /* availabilityAvailable */  availability != null,
                /* customerAvailable */      customer != null || !customerRequested,
                /* pricingError */           pricing == null ? "Pricing information is temporarily unavailable" : null,
                /* availabilityError */      availability == null ? "Stock information is temporarily unavailable" : null,
                /* customerError */          customer == null && customerRequested ? "Customer personalization is temporarily unavailable" : null);
    }
}
