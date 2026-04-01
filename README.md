# Product Information Aggregator

A backend service that aggregates product information from multiple upstream services into a single, market-aware response for a B2B e-commerce platform.

## How to Run

### Prerequisites

- **Java 21+** installed (`java -version` to verify)
- No Maven installation required — the project includes Maven Wrapper

### Build & Run

```bash
# Run tests
./mvnw clean test

# Start the application
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

### API Documentation

Once the service is running, interactive Swagger UI is available at:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Example Requests

```bash
# Basic product lookup (Dutch market)
curl "http://localhost:8080/api/v1/products/PRD-001?market=nl-NL"

# Product with customer-specific pricing (German market, Gold dealer)
curl "http://localhost:8080/api/v1/products/PRD-001?market=de-DE&customerId=CUST-GOLD"

# Polish market — localized product names
curl "http://localhost:8080/api/v1/products/PRD-002?market=pl-PL"

# Health check
curl "http://localhost:8080/actuator/health"
```

### Available Test Data

| Products  | Customers    | Markets          |
|-----------|-------------|------------------|
| `PRD-001` | `CUST-GOLD`   | `nl-NL`, `de-DE` |
| `PRD-002` | `CUST-SILVER`  | `pl-PL`, `fr-FR` |
|           | `CUST-BRONZE`  |                  |

## Key Design Decisions

### 1. Parallel Upstream Calls with Virtual Threads

All four upstream services are called concurrently using Java 21+ virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`). Virtual threads are lightweight (no 1:1 OS thread mapping), so spinning up one per upstream call has negligible overhead — even under high concurrency. Since the slowest service (Availability) has ~100ms latency, parallel execution keeps the aggregated response time close to the slowest individual call rather than the sum of all calls. This is critical for mobile users on slow rural connections.

Tomcat is also configured to handle incoming requests on virtual threads (`spring.threads.virtual.enabled=true`), eliminating the platform thread pool as a scalability bottleneck.

### 2. Required vs Optional Data Sources

The aggregator distinguishes between **required** (Catalog) and **optional** (Pricing, Availability, Customer) upstream services:

- **Catalog failure → HTTP 503** — we can't display a product without its basic info.
- **Pricing failure → product returned, price marked unavailable** — a customer can still browse the product.
- **Availability failure → product returned, stock marked unknown** — better to show the product than nothing.
- **Customer failure → non-personalized response** — graceful fallback to standard pricing.

This is implemented with a `DataStatus` object in every response, so the frontend knows exactly which data sources succeeded and can display appropriate UI (e.g., "Price temporarily unavailable" instead of a broken page).

### 3. Interface-Based Upstream Services

Each upstream service is defined as a Java interface (`CatalogService`, `PricingService`, etc.) with a mock implementation. This means:

- **Testability** — unit tests inject stubs with deterministic behavior (no random failures).
- **Swappability** — replacing mocks with real HTTP clients (e.g., `WebClient`, `RestClient`) requires only a new implementation class, no changes to the aggregator.
- **New data sources** — adding a new upstream (e.g., Related Products) means creating a new interface + implementation and wiring it into `ProductAggregatorService`.

### 4. Realistic Mock Behavior

Mocks simulate real distributed system behavior:
- **Latency** — each service has a base latency with ±30% jitter (normal distribution).
- **Failures** — random failures based on the specified reliability percentages (98–99.9%).
- **Market awareness** — prices reflect local currency (EUR/PLN/GBP), product names are localized per market.

### 5. Timeout Protection

A configurable timeout (`aggregator.timeout-ms`, default 500ms) applies to each upstream call individually. This prevents a single slow service from blocking the entire response.

## Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Virtual threads over reactive (`WebFlux`) | Simpler blocking code, easier debugging, no reactive learning curve — virtual threads provide comparable scalability | Requires Java 21+, young runtime feature |
| Single timeout for all services | Simple configuration | Can't tune per-service (e.g., give Availability more time) |
| In-process mocks over separate microservices | Zero setup, runs anywhere with just `java` | Doesn't test network failure modes (DNS, TCP timeouts) |
| Java records for DTOs | Immutable, concise, no boilerplate | Requires Java 16+ |

## What I Would Do Differently With More Time

- **Per-service timeouts and circuit breakers** (e.g., Resilience4j) — if Availability starts failing consistently, stop calling it for a cooldown period instead of waiting for each timeout.
- **Caching layer** — product catalog data changes infrequently; a short TTL cache (e.g., Caffeine) would reduce upstream load and improve P99 latency.
- **Observability** — Micrometer metrics for per-service latency, failure rates, and cache hit ratios. Structured logging with correlation IDs for request tracing.
- **Contract tests** — define expected upstream API contracts so changes in upstream services are caught early.

## Design Question: Option A — Adding a Related Products Service

> "The Assortment team wants to add a 'Related Products' service (200ms latency, 90% reliability). How would your design accommodate this?"

**It should be optional.** The core product page works without related products — they're a supplementary "you might also like" section. With 90% reliability, making it required would mean 1 in 10 product views would fail entirely, which is unacceptable.

**How to add it:**

1. Create a `RelatedProductsService` interface and its mock implementation.
2. Add a `CompletableFuture` call in `ProductAggregatorService.getProduct()` alongside the existing parallel calls.
3. Add a `relatedProducts` field to `AggregatedProductResponse` (nullable) and track its status in `DataStatus`.

The existing architecture supports this with no structural changes — it's the same pattern as Availability or Customer data.

**Latency concern:** At 200ms base latency, Related Products would become the slowest upstream call and could dominate the overall response time. Two mitigations:
- **Aggressive caching** — related products change infrequently, so a 5–10 minute cache would serve most requests instantly.
- **Separate timeout** — give it a tighter timeout (e.g., 150ms) than other services, accepting a higher miss rate in exchange for not slowing down the critical path.

If the frontend supports it, an even better approach is to **load related products asynchronously** via a separate endpoint, so the main product page renders immediately.
