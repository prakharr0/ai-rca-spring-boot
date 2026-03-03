package io.github.prakharr0.ai.rca.spring.boot.core.context;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback appender that forwards log events into the in-memory
 * {@link LogBuffer} for diagnostic and AI-based root cause analysis.
 *
 * <p>
 * This appender captures formatted log messages and stores them in
 * a circular buffer, enabling retrieval of recent logs during exception
 * analysis or AI prompt construction.
 *
 * <h2>Thread Safety</h2>
 * LogBuffer operations are synchronized and safe for concurrent use.
 *
 * @see LogBuffer
 */
public class RingBufferLogAppender extends AppenderBase<ILoggingEvent> {

    /**
     * Appends a formatted log event message to the {@link LogBuffer}.
     *
     * <p>
     * This method is invoked by Logback for each logging event.
     * Only the formatted message is stored (metadata such as MDC
     * or throwable information is not captured).
     *
     * @param eventObject the logging event; never {@code null}
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        LogBuffer.append(eventObject.getFormattedMessage());
    }
}