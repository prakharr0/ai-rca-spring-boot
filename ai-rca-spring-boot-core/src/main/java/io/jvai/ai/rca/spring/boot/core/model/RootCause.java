package io.jvai.ai.rca.spring.boot.core.model;

public record RootCause(
        int rank,
        String title,
        String likelihood,
        String category,
        String reasoning,
        String diagnosticStep,
        String estimatedTimeToVerify
) {}
