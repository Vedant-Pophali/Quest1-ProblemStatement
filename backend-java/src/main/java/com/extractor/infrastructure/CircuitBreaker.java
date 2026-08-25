package com.extractor.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final int failureThreshold;
    private final long retryTimePeriodMs;

    private int failureCount = 0;
    private long lastFailureTime = 0;
    private State state = State.CLOSED;

    public enum State {
        CLOSED,     // Operating normally
        OPEN,       // Failing fast, preventing execution
        HALF_OPEN   // Testing if the system has recovered
    }

    public CircuitBreaker(int failureThreshold, long retryTimePeriodMs) {
        this.failureThreshold = failureThreshold;
        this.retryTimePeriodMs = retryTimePeriodMs;
    }

    /**
     * Wraps a Callable action (like spawning a Python process) with fail-fast logic.
     */
    public synchronized <T> T execute(Callable<T> action) throws Exception {
        evaluateState();

        if (state == State.OPEN) {
            throw new RuntimeException("Circuit Breaker is OPEN. Failing fast to prevent cascading system hangs.");
        }

        try {
            T result = action.call();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e; // Rethrow to let the ChunkManager handle the chunk retry/failure
        }
    }

    private void evaluateState() {
        if (state == State.OPEN) {
            long elapsedTime = System.currentTimeMillis() - lastFailureTime;
            // If enough time has passed, let one request through to test recovery
            if (elapsedTime > retryTimePeriodMs) {
                log.info("Circuit Breaker transitioning to HALF_OPEN to test worker recovery.");
                state = State.HALF_OPEN;
            }
        }
    }

    private void recordSuccess() {
        if (state == State.HALF_OPEN) {
            log.info("Worker recovered. Circuit Breaker transitioning back to CLOSED.");
        }
        failureCount = 0;
        state = State.CLOSED;
    }

    private void recordFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        
        if (failureCount >= failureThreshold) {
            log.error("Circuit Breaker threshold reached ({} consecutive failures). Transitioning to OPEN.", failureCount);
            state = State.OPEN;
        } else {
            log.warn("Subprocess failed. Failure count: {}/{}", failureCount, failureThreshold);
        }
    }
}