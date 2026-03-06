package io.github.prakharr0.ai.rca.spring.boot.core.model.chat;

import java.time.Instant;
import java.util.List;

public record ChatAnswer(
        String answer,
        List<String> referencedEventIds,
        Instant resolvedTime
) {
}
