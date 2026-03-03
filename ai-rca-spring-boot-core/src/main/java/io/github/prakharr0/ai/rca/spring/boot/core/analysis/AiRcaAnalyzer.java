package io.github.prakharr0.ai.rca.spring.boot.core.analysis;

/**
 * Contract for AI-powered Root Cause Analysis (RCA) of runtime exceptions.
 *
 * <p>
 * Implementations of this interface are responsible for analyzing a given
 * {@link Throwable} and generating diagnostic insights using an AI model
 * or any other intelligent analysis mechanism.
 *
 * <h2>Typical Responsibilities</h2>
 * <ul>
 *     <li>Collect contextual information related to the exception</li>
 *     <li>Generate structured or unstructured diagnostic output</li>
 *     <li>Optionally cache or persist analysis results</li>
 *     <li>Log or expose the generated RCA findings</li>
 * </ul>
 *
 * <h2>Execution Model</h2>
 * Implementations may choose to:
 * <ul>
 *     <li>Execute synchronously</li>
 *     <li>Execute asynchronously (e.g., using {@code @Async})</li>
 *     <li>Delegate analysis to remote AI services</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Implementations should document their thread-safety guarantees,
 * especially if used in high-concurrency environments such as
 * Spring Boot web applications.
 *
 * @see Throwable
 */
public interface AiRcaAnalyzer {

    /**
     * Performs root cause analysis for the given {@link Throwable}.
     *
     * <p>
     * The implementation may:
     * <ul>
     *     <li>Collect contextual metadata</li>
     *     <li>Generate an AI-based explanation</li>
     *     <li>Cache results for similar failures</li>
     *     <li>Log or publish analysis output</li>
     * </ul>
     *
     * <p>
     * This method does not define whether execution is synchronous
     * or asynchronous. Implementations are free to decide.
     *
     * @param throwable the exception to analyze; must not be {@code null}
     */
    void analyze(Throwable throwable);
}
