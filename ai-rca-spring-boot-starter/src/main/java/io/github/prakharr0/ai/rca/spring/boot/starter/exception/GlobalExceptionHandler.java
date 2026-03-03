package io.github.prakharr0.ai.rca.spring.boot.starter.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;

/**
 * Global exception handler that integrates AI-based root cause analysis
 * with Spring MVC exception handling.
 *
 * <p>
 * This handler intercepts uncaught exceptions, triggers AI analysis via
 * {@link AiRcaAnalyzer}, and then rethrows the exception to preserve default
 * error handling semantics.
 *
 * <h2>Behavior</h2>
 * <ol>
 *     <li>Logs exception occurrence</li>
 *     <li>Invokes AI root cause analysis asynchronously (if configured)</li>
 *     <li>Rethrows the original exception</li>
 * </ol>
 *
 * <h2>Design Considerations</h2>
 * <ul>
 *     <li>Does not swallow exceptions</li>
 *     <li>Preserves existing error handling</li>
 *     <li>Enables diagnostic analysis without altering response behavior</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This handler is stateless aside from injected dependencies and is safe
 * for singleton usage within Spring’s component model.
 *
 * @see ControllerAdvice
 * @see ExceptionHandler
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Logger for exception handling and diagnostic events.
     */
    private final static Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * AI analyzer used to perform root cause analysis.
     */
    private final AiRcaAnalyzer analyzer;

    /**
     * Creates a new global exception handler.
     *
     * @param analyzer AI root cause analyzer
     */
    public GlobalExceptionHandler(AiRcaAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Handles all uncaught exceptions.
     *
     * <p>
     * The handler:
     * <ul>
     *     <li>Logs the exception occurrence</li>
     *     <li>Triggers AI-based analysis</li>
     *     <li>Rethrows the original exception</li>
     * </ul>
     *
     * @param ex the exception to handle
     * @throws Exception rethrows the original exception
     */
    @ExceptionHandler(Exception.class)
    public void handle(Exception ex) throws Exception {
        log.info("[AI-RCA-SPRING-BOOT-STARTER] Analysis starting for: {}", ex.getLocalizedMessage());
        analyzer.analyze(ex);
        throw ex;
    }
}