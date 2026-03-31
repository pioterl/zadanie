package com.kramp.aggregator.controller;

import com.kramp.aggregator.model.AggregatedProductResponse;
import com.kramp.aggregator.service.ProductAggregatorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final String MARKET_CODE_REGEX = "^[a-z]{2}-[A-Z]{2}$";
    private static final String MARKET_CODE_MESSAGE = "Market code must follow BCP 47 format (e.g., 'nl-NL', 'de-DE', 'pl-PL')";

    private final ProductAggregatorService aggregatorService;

    public ProductController(ProductAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<AggregatedProductResponse> getProduct(
            @PathVariable @NotBlank String productId,
            @RequestParam @Pattern(regexp = MARKET_CODE_REGEX, message = MARKET_CODE_MESSAGE) String market,
            @RequestParam(required = false) String customerId
    ) {
        AggregatedProductResponse response = aggregatorService.getProduct(productId, market, customerId);
        return ResponseEntity.ok(response);
    }
}
