package io.github.prakharr0.ai.rca.spring.boot.core.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogBufferTest {

    private static final int MAX_LINES = 50;

    @Test
    void appendedLineAppearsInDump() {
        String unique = "log-buffer-test-" + System.nanoTime();
        LogBuffer.append(unique);
        assertThat(LogBuffer.dump()).contains(unique);
    }

    @Test
    void dumpJoinsLinesWithNewline() {
        String a = "alpha-" + System.nanoTime();
        String b = "beta-" + System.nanoTime();
        LogBuffer.append(a);
        LogBuffer.append(b);
        String dump = LogBuffer.dump();
        assertThat(dump).contains(a);
        assertThat(dump).contains(b);
        assertThat(dump).contains("\n");
    }

    @Test
    void evictsOldestWhenFull() {
        // Append MAX_LINES + 1 unique lines; the first must be evicted.
        String evicted = "evicted-" + System.nanoTime();
        LogBuffer.append(evicted);
        // Fill the buffer past capacity to guarantee eviction of 'evicted'
        for (int i = 0; i < MAX_LINES; i++) {
            LogBuffer.append("filler-" + i + "-" + System.nanoTime());
        }
        assertThat(LogBuffer.dump()).doesNotContain(evicted);
    }

    @Test
    void bufferNeverExceedsMaxLines() {
        for (int i = 0; i < MAX_LINES + 20; i++) {
            LogBuffer.append("line-" + i);
        }
        long lineCount = LogBuffer.dump().lines().count();
        assertThat(lineCount).isLessThanOrEqualTo(MAX_LINES);
    }

    @Test
    void appendNull_doesNotThrow() {
        // LogBuffer.append accepts null without NPE — the buffer stores it as a line
        LogBuffer.append(null);
    }
}
