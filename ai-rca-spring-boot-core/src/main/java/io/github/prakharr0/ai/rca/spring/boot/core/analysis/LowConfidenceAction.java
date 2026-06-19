package io.github.prakharr0.ai.rca.spring.boot.core.analysis;

/**
 * Defines how the analyzer behaves when analysis confidence falls below
 * the configured {@code ai.rca.min-confidence} threshold.
 */
public enum LowConfidenceAction {

    /**
     * Store the result but tag it with {@code lowConfidence: true}.
     * Consumers can filter on this flag. Default behavior.
     */
    LOG_WARN,

    /**
     * Discard the result entirely and mark the event as failed.
     * Use when downstream consumers must not receive low-quality analyses.
     */
    SKIP
}