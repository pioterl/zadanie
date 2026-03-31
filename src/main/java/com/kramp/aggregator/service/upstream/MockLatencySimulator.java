package com.kramp.aggregator.service.upstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared utility for simulating realistic upstream service behavior:
 * artificial latency (normal distribution around a base) and random failures.
 */
final class MockLatencySimulator {

    private static final Logger log = LoggerFactory.getLogger(MockLatencySimulator.class);

    private MockLatencySimulator() {}

    static void simulateLatency(String serviceName, long baseMs) {
        // Add jitter: ±30% around base latency
        long jitter = (long) (baseMs * 0.3);
        long delay = baseMs + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        delay = Math.max(10, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(serviceName + " interrupted during simulated latency", e);
        }
        log.debug("{} responded in ~{}ms", serviceName, delay);
    }

    static void simulateFailure(String serviceName, double reliabilityPercent) {
        double roll = ThreadLocalRandom.current().nextDouble() * 100;
        if (roll > reliabilityPercent) {
            log.warn("{} simulated failure (roll={}, reliability={}%)", serviceName, roll, reliabilityPercent);
            throw new RuntimeException(serviceName + " is temporarily unavailable");
        }
    }
}
