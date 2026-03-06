package io.github.prakharr0.ai.rca.spring.boot.starter.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ExceptionFingerprint;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.UUID;

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
    private final ContextCollector collector;
    private final ExceptionTimelineStore timelineStore;

    /**
     * Creates a new global exception handler.
     *
     * @param analyzer AI root cause analyzer
     */
    public GlobalExceptionHandler(AiRcaAnalyzer analyzer, ContextCollector collector, ExceptionTimelineStore timelineStore) {
        this.analyzer = analyzer;
        this.collector = collector;
        this.timelineStore = timelineStore;
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
    public void handle(Exception ex, WebRequest request) throws Exception {
        log.info("[AI-RCA-SPRING-BOOT-STARTER] Analysis starting for: {}", ex.getLocalizedMessage());
        ContextSnapshot snapshot = collector.collect(ex);
        String fingerprint = ExceptionFingerprint.generate(snapshot);

        ExceptionOccurrence occurrence = new ExceptionOccurrence(
                UUID.randomUUID().toString(),
                Instant.now(),
                snapshot.exceptionType(),
                snapshot.rootCauseType(),
                snapshot.rootCauseMessage(),
                fingerprint,
                "N/A",
                request == null ? "N/A" : request.getDescription(false),
                Thread.currentThread().getName()
        );

        timelineStore.add(occurrence);
        analyzer.analyze(ex);
        throw ex;
    }
}
