package com.kramp.aggregator.service.upstream;

import com.kramp.aggregator.model.ProductInfo;
import com.kramp.aggregator.model.ProductInfo.Spec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MockCatalogService implements CatalogService {

    private static final double RELIABILITY = 99.9;
    private static final long BASE_LATENCY_MS = 50;

    private static final Map<String, Map<String, String>> LOCALIZED_NAMES = Map.of(
            "nl-NL", Map.of(
                    "PRD-001", "Hydraulische Cilinder 50mm",
                    "PRD-002", "Luchtfilter Premium"
            ),
            "de-DE", Map.of(
                    "PRD-001", "Hydraulikzylinder 50mm",
                    "PRD-002", "Luftfilter Premium"
            ),
            "pl-PL", Map.of(
                    "PRD-001", "Cylinder Hydrauliczny 50mm",
                    "PRD-002", "Filtr Powietrza Premium"
            )
    );

    private static final Map<String, Map<String, String>> LOCALIZED_DESCRIPTIONS = Map.of(
            "nl-NL", Map.of(
                    "PRD-001", "Dubbelwerkende hydraulische cilinder geschikt voor landbouwmachines.",
                    "PRD-002", "Hoogwaardige luchtfilter voor dieselmotoren."
            ),
            "de-DE", Map.of(
                    "PRD-001", "Doppeltwirkender Hydraulikzylinder geeignet für Landmaschinen.",
                    "PRD-002", "Hochwertiger Luftfilter für Dieselmotoren."
            ),
            "pl-PL", Map.of(
                    "PRD-001", "Dwustronnego działania cylinder hydrauliczny do maszyn rolniczych.",
                    "PRD-002", "Wysokiej jakości filtr powietrza do silników diesla."
            )
    );

    private static final Map<String, String> DEFAULT_NAMES = Map.of(
            "PRD-001", "Hydraulic Cylinder 50mm",
            "PRD-002", "Air Filter Premium"
    );

    private static final Map<String, String> DEFAULT_DESCRIPTIONS = Map.of(
            "PRD-001", "Double-acting hydraulic cylinder suitable for agricultural machinery.",
            "PRD-002", "High-quality air filter for diesel engines."
    );

    @Override
    public ProductInfo getProduct(String productId, String market) {
        MockLatencySimulator.simulateLatency("CatalogService", BASE_LATENCY_MS);
        MockLatencySimulator.simulateFailure("CatalogService", RELIABILITY);

        String name = LOCALIZED_NAMES
                .getOrDefault(market, Map.of())
                .getOrDefault(productId, DEFAULT_NAMES.getOrDefault(productId, productId));

        String description = LOCALIZED_DESCRIPTIONS
                .getOrDefault(market, Map.of())
                .getOrDefault(productId, DEFAULT_DESCRIPTIONS.getOrDefault(productId, ""));

        return switch (productId) {
            case "PRD-001" -> new ProductInfo(
                    productId,
                    name,
                    description,
                    "Hydraulics",
                    List.of("/images/prd-001-front.jpg", "/images/prd-001-side.jpg"),
                    List.of(
                            new Spec("Bore Diameter", "50", "mm"),
                            new Spec("Stroke Length", "300", "mm"),
                            new Spec("Max Pressure", "210", "bar"),
                            new Spec("Weight", "4.2", "kg")
                    )
            );
            case "PRD-002" -> new ProductInfo(
                    productId,
                    name,
                    description,
                    "Filters",
                    List.of("/images/prd-002-front.jpg"),
                    List.of(
                            new Spec("Height", "250", "mm"),
                            new Spec("Outer Diameter", "135", "mm"),
                            new Spec("Filtration Efficiency", "99.5", "%")
                    )
            );
            default -> throw new IllegalArgumentException("Product not found: " + productId);
        };
    }
}
