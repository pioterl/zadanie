package com.kramp.aggregator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getProduct_validRequest_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products/PRD-001")
                        .param("market", "nl-NL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.productId").value("PRD-001"))
                .andExpect(jsonPath("$.market").value("nl-NL"))
                .andExpect(jsonPath("$.dataStatus").exists());
    }

    @Test
    void getProduct_withCustomerId_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products/PRD-001")
                        .param("market", "de-DE")
                        .param("customerId", "CUST-GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.productId").value("PRD-001"))
                .andExpect(jsonPath("$.market").value("de-DE"));
    }

    @Test
    void getProduct_unknownProduct_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/UNKNOWN")
                        .param("market", "nl-NL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_missingMarket_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/PRD-001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProduct_invalidMarketCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/PRD-001")
                        .param("market", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getProduct_polishMarket_returnsLocalizedContent() throws Exception {
        mockMvc.perform(get("/api/v1/products/PRD-001")
                        .param("market", "pl-PL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.name").value("Cylinder Hydrauliczny 50mm"));
    }
}
