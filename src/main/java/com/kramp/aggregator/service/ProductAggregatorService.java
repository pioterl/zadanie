package com.kramp.aggregator.service;

import com.kramp.aggregator.exception.CatalogUnavailableException;
import com.kramp.aggregator.exception.ProductNotFoundException;
import com.kramp.aggregator.model.*;
import com.kramp.aggregator.service.upstream.AvailabilityService;
import com.kramp.aggregator.service.upstream.CatalogService;
import com.kramp.aggregator.service.upstream.CustomerService;
import com.kramp.aggregator.service.upstream.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.*;

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

        UpstreamFutures futures = dispatchUpstreamCalls(productId, market, customerId);

        ProductInfo product = fetchCatalog(futures.catalog(), productId);
        PricingInfo pricing = getOptionalResult(futures.pricing(), "PricingService");
        AvailabilityInfo availability = getOptionalResult(futures.availability(), "AvailabilityService");
        CustomerInfo customer = getOptionalResult(futures.customer(), "CustomerService");

        AggregatedProductResponse.DataStatus dataStatus = buildDataStatus(pricing, availability, customer, customerId);

        return new AggregatedProductResponse(product, pricing, availability, customer, market, dataStatus);
    }

    private UpstreamFutures dispatchUpstreamCalls(String productId, String market, String customerId) {
        CompletableFuture<ProductInfo> catalog = CompletableFuture
                .supplyAsync(() -> catalogService.getProduct(productId, market), executor);
        CompletableFuture<PricingInfo> pricing = CompletableFuture
                .supplyAsync(() -> pricingService.getPricing(productId, market, customerId), executor);
        CompletableFuture<AvailabilityInfo> availability = CompletableFuture
                .supplyAsync(() -> availabilityService.getAvailability(productId, market), executor);
        CompletableFuture<CustomerInfo> customer = (customerId != null)
                ? CompletableFuture.supplyAsync(() -> customerService.getCustomer(customerId), executor)
                : CompletableFuture.completedFuture(null);

        return new UpstreamFutures(catalog, pricing, availability, customer);
    }

    private ProductInfo fetchCatalog(CompletableFuture<ProductInfo> future, String productId) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                throw new ProductNotFoundException("Product not found: " + productId, cause);
            }
            throw new CatalogUnavailableException("Catalog service failed for product: " + productId, cause);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new CatalogUnavailableException("Catalog service timed out for product: " + productId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException("Catalog request interrupted", e);
        }
    }

    private <T> T getOptionalResult(CompletableFuture<T> future, String serviceName) {
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

    private AggregatedProductResponse.DataStatus buildDataStatus(
            PricingInfo pricing, AvailabilityInfo availability, CustomerInfo customer, String customerId
    ) {
        return new AggregatedProductResponse.DataStatus(
                pricing != null,
                availability != null,
                customer != null || customerId == null,
                pricing == null ? "Pricing information is temporarily unavailable" : null,
                availability == null ? "Stock information is temporarily unavailable" : null,
                customer == null && customerId != null ? "Customer personalization is temporarily unavailable" : null
        );
    }

    private record UpstreamFutures(
            CompletableFuture<ProductInfo> catalog,
            CompletableFuture<PricingInfo> pricing,
            CompletableFuture<AvailabilityInfo> availability,
            CompletableFuture<CustomerInfo> customer
    ) {}
}
