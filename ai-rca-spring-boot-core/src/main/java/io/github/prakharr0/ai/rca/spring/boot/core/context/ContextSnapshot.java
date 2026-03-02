package io.github.prakharr0.ai.rca.spring.boot.core.context;

public record ContextSnapshot(
        String exceptionType,
        String rootCauseType,
        String rootCauseMessage,
        String stackTrace,
        String recentLogs,
        String springVersion,
        String javaVersion,
        String activeProfiles,
        String packaging,
        String deploymentEnvironment,
        String database,
        String webStack,
        String buildTool
) {}