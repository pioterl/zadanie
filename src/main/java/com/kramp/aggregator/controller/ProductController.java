package com.kramp.aggregator.controller;

import com.kramp.aggregator.model.AggregatedProductResponse;
import com.kramp.aggregator.service.ProductAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Aggregator", description = "Aggregates product information from multiple upstream services")
public class ProductController {

    private static final String MARKET_CODE_REGEX = "^[a-z]{2}-[A-Z]{2}$";
    private static final String MARKET_CODE_MESSAGE = "Market code must follow BCP 47 format (e.g., 'nl-NL', 'de-DE', 'pl-PL')";

    private final ProductAggregatorService aggregatorService;

    public ProductController(ProductAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/{productId}")
    @Operation(
            summary = "Get aggregated product information",
            description = "Fetches product details, pricing, availability, and optional customer context "
                    + "from upstream services in parallel. Returns a single aggregated response suitable for display. "
                    + "If optional services fail, the response includes partial data with status indicators."
    )
    @ApiResponse(responseCode = "200", description = "Product information aggregated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid market code or missing required parameters",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @ApiResponse(responseCode = "404", description = "Product not found in catalog",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    @ApiResponse(responseCode = "503", description = "Catalog service unavailable — product cannot be displayed",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public ResponseEntity<AggregatedProductResponse> getProduct(
            @PathVariable @NotBlank
            @Parameter(description = "Product identifier", example = "PRD-001")
            String productId,

            @RequestParam @Pattern(regexp = MARKET_CODE_REGEX, message = MARKET_CODE_MESSAGE)
            @Parameter(description = "Market/language code in BCP 47 format", example = "nl-NL")
            String market,

            @RequestParam(required = false)
            @Parameter(description = "Customer identifier for personalized pricing (optional)", example = "CUST-GOLD")
            String customerId
    ) {
        AggregatedProductResponse response = aggregatorService.getProduct(productId, market, customerId);
        return ResponseEntity.ok(response);
    }
}
