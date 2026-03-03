package io.github.prakharr0.ai.rca.spring.boot.core.model;

import java.util.List;

/**
 * Represents the structured result of an AI-powered Root Cause Analysis.
 *
 * <p>This record is returned after an exception is intercepted and analyzed.
 * It contains ranked root cause hypotheses, a confidence score, and any
 * missing context that could improve the analysis.
 *
 * @param analysisConfidence  A score between {@code 0.0} and {@code 1.0} indicating
 *                            how confident the AI is in the analysis.
 *                            Higher values indicate stronger signal from the available context.
 * @param knownPattern        A named pattern the AI matched this exception against, if any.
 *                            Example: {@code "Spring DataSource misconfiguration"}.
 *                            {@code null} or empty if no known pattern was matched.
 * @param exceptionMessage    The exception message being analyzed
 * @param missingInformation  A list of context items that were absent but would have
 *                            improved the accuracy of the analysis (e.g., logs, env vars).
 *                            Empty if all necessary context was available.
 * @param rootCauses          An ordered list of {@link RootCause} hypotheses, ranked
 *                            from most to least likely.
 *
 * @see RootCause
 */
public record AiRcaResponse(
        double analysisConfidence,
        String knownPattern,
        String exceptionMessage,
        List<String> missingInformation,
        List<RootCause> rootCauses
) {}
