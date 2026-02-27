package rathi.prakhar.ai.rca.spring.boot.core.context;

import java.util.ArrayDeque;
import java.util.Deque;

public class LogBuffer {

    private static final int MAX_LINES = 50;
    private static final Deque<String> buffer = new ArrayDeque<>();

    public static synchronized void append(String line) {
        if (buffer.size() >= MAX_LINES) {
            buffer.pollFirst();
        }
        buffer.addLast(line);
    }

    public static synchronized String dump() {
        return String.join("\n", buffer);
    }
}