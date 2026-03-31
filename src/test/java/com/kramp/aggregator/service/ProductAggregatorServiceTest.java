package com.kramp.aggregator.service;

import com.kramp.aggregator.exception.CatalogUnavailableException;
import com.kramp.aggregator.exception.ProductNotFoundException;
import com.kramp.aggregator.model.*;
import com.kramp.aggregator.service.upstream.AvailabilityService;
import com.kramp.aggregator.service.upstream.CatalogService;
import com.kramp.aggregator.service.upstream.CustomerService;
import com.kramp.aggregator.service.upstream.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductAggregatorServiceTest {

    private ProductAggregatorService service;

    private CatalogService catalogService;
    private PricingService pricingService;
    private AvailabilityService availabilityService;
    private CustomerService customerService;

    private final ProductInfo sampleProduct = new ProductInfo(
            "PRD-001", "Test Product", "Desc", "Category",
            List.of("/img.jpg"), List.of(new ProductInfo.Spec("Weight", "1", "kg"))
    );

    private final PricingInfo samplePricing = new PricingInfo(
            "PRD-001", "EUR", new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00")
    );

    private final AvailabilityInfo sampleAvailability = new AvailabilityInfo(
            "PRD-001", 25, "Rotterdam, NL", "Next business day", AvailabilityInfo.StockStatus.IN_STOCK
    );

    private final CustomerInfo sampleCustomer = new CustomerInfo(
            "CUST-GOLD", "Gold Dealer", List.of("hydraulics")
    );

    @BeforeEach
    void setUp() {
        // Default: all services succeed instantly
        catalogService = (productId, market) -> sampleProduct;
        pricingService = (productId, market, customerId) -> samplePricing;
        availabilityService = (productId, market) -> sampleAvailability;
        customerService = (customerId) -> sampleCustomer;
    }

    private ProductAggregatorService createService() {
        return new ProductAggregatorService(
                catalogService, pricingService, availabilityService, customerService, 2000
        );
    }

    @Test
    void allServicesSucceed_returnsFullResponse() {
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "nl-NL", "CUST-GOLD");

        assertNotNull(response.product());
        assertNotNull(response.pricing());
        assertNotNull(response.availability());
        assertNotNull(response.customer());
        assertEquals("nl-NL", response.market());
        assertTrue(response.dataStatus().pricingAvailable());
        assertTrue(response.dataStatus().availabilityAvailable());
        assertTrue(response.dataStatus().customerAvailable());
    }

    @Test
    void noCustomerId_returnsResponseWithoutCustomerData() {
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "de-DE", null);

        assertNotNull(response.product());
        assertNull(response.customer());
        assertTrue(response.dataStatus().customerAvailable()); // no customer ID = not an error
    }

    @Test
    void catalogFails_throwsCatalogUnavailableException() {
        catalogService = (productId, market) -> {
            throw new RuntimeException("Catalog down");
        };
        service = createService();

        assertThrows(CatalogUnavailableException.class,
                () -> service.getProduct("PRD-001", "nl-NL", null));
    }

    @Test
    void catalogReturnsProductNotFound_throwsProductNotFoundException() {
        catalogService = (productId, market) -> {
            throw new IllegalArgumentException("Product not found: " + productId);
        };
        service = createService();

        assertThrows(ProductNotFoundException.class,
                () -> service.getProduct("UNKNOWN", "nl-NL", null));
    }

    @Test
    void pricingFails_returnsProductWithPricingUnavailable() {
        pricingService = (productId, market, customerId) -> {
            throw new RuntimeException("Pricing down");
        };
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "nl-NL", null);

        assertNotNull(response.product());
        assertNull(response.pricing());
        assertFalse(response.dataStatus().pricingAvailable());
        assertNotNull(response.dataStatus().pricingError());
    }

    @Test
    void availabilityFails_returnsProductWithStockUnknown() {
        availabilityService = (productId, market) -> {
            throw new RuntimeException("Availability down");
        };
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "nl-NL", null);

        assertNotNull(response.product());
        assertNull(response.availability());
        assertFalse(response.dataStatus().availabilityAvailable());
        assertNotNull(response.dataStatus().availabilityError());
    }

    @Test
    void customerFails_returnsNonPersonalizedResponse() {
        customerService = (customerId) -> {
            throw new RuntimeException("Customer service down");
        };
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "nl-NL", "CUST-GOLD");

        assertNotNull(response.product());
        assertNull(response.customer());
        assertFalse(response.dataStatus().customerAvailable());
        assertNotNull(response.dataStatus().customerError());
    }

    @Test
    void allOptionalServicesFail_returnsProductOnly() {
        pricingService = (productId, market, customerId) -> {
            throw new RuntimeException("down");
        };
        availabilityService = (productId, market) -> {
            throw new RuntimeException("down");
        };
        customerService = (customerId) -> {
            throw new RuntimeException("down");
        };
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "nl-NL", "CUST-GOLD");

        assertNotNull(response.product());
        assertNull(response.pricing());
        assertNull(response.availability());
        assertNull(response.customer());
    }

    @Test
    void slowCatalog_throwsCatalogUnavailableOnTimeout() {
        catalogService = (productId, market) -> {
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return sampleProduct;
        };
        service = createService();

        assertThrows(CatalogUnavailableException.class,
                () -> service.getProduct("PRD-001", "nl-NL", null));
    }

    @Test
    void slowOptionalService_returnsGracefullyDegraded() {
        pricingService = (productId, market, customerId) -> {
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return samplePricing;
        };
        service = createService();

        AggregatedProductResponse response = service.getProduct("PRD-001", "nl-NL", null);

        assertNotNull(response.product());
        assertNull(response.pricing()); // timed out
        assertFalse(response.dataStatus().pricingAvailable());
    }
}
