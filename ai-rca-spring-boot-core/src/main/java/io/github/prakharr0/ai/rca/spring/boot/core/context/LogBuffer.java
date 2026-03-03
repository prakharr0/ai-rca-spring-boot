package io.github.prakharr0.ai.rca.spring.boot.core.context;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * In-memory circular log buffer used to retain recent log lines
 * for diagnostic and AI-based root cause analysis purposes.
 *
 * <p>
 * This buffer stores a fixed number of recent log entries
 * (defined by {@code MAX_LINES}) using a FIFO eviction strategy.
 * When the buffer reaches capacity, the oldest entry is removed
 * before adding a new one.
 *
 * <h2>Purpose</h2>
 * The primary goal of this class is to:
 * <ul>
 *     <li>Provide contextual log history during exception analysis</li>
 *     <li>Improve AI diagnostic quality with recent runtime signals</li>
 *     <li>Remain lightweight and dependency-free</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All operations are synchronized at the method level to ensure
 * thread-safe access in concurrent environments.
 *
 * <h2>Design Characteristics</h2>
 * <ul>
 *     <li>Static global buffer (application-wide)</li>
 *     <li>Fixed-size memory footprint</li>
 *     <li>FIFO eviction policy</li>
 * </ul>
 */
public class LogBuffer {

    /**
     * Maximum number of log lines retained in memory.
     */
    private static final int MAX_LINES = 50;

    /**
     * Internal storage for log lines using a double-ended queue.
     * The oldest entry is stored at the head of the deque.
     */
    private static final Deque<String> buffer = new ArrayDeque<>();

    /**
     * Appends a log line to the buffer.
     *
     * <p>
     * If the buffer has reached {@code MAX_LINES}, the oldest
     * entry is removed before inserting the new line.
     *
     * <p>
     * This method is synchronized to ensure thread-safe access.
     *
     * @param line the log line to append; may be {@code null}
     */
    public static synchronized void append(String line) {
        if (buffer.size() >= MAX_LINES) {
            buffer.pollFirst();
        }
        buffer.addLast(line);
    }


    /**
     * Returns all buffered log lines as a single newline-delimited string.
     *
     * <p>
     * The returned value represents a snapshot of the buffer
     * at the time of invocation.
     *
     * <p>
     * This method is synchronized to ensure thread-safe access.
     *
     * @return concatenated log lines separated by {@code '\n'},
     *         or an empty string if no logs are present
     */
    public static synchronized String dump() {
        return String.join("\n", buffer);
    }
}