package io.github.prakharr0.ai.rca.tests.eval;

import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;

import java.util.List;

/**
 * Represents a single eval scenario loaded from a JSON fixture file.
 *
 * <p>Each fixture contains the full context that would be collected from a real exception
 * (matching {@link ContextSnapshot} fields), plus assertions used by eval tests:
 * {@code expectedCategory} and {@code expectedRank1Keywords}.
 *
 * <p>{@link #toContextSnapshot()} bridges the fixture into the library's prompt-building
 * pipeline, so evals exercise the exact same path as production.
 */
public record EvalFixture(
        String id,
        String description,
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
        String buildTool,
        String expectedCategory,
        List<String> expectedRank1Keywords
) {

    /**
     * Converts this fixture into a {@link ContextSnapshot} compatible with
     * {@link io.github.prakharr0.ai.rca.spring.boot.core.prompt.UserPromptBuilder#build(ContextSnapshot)}.
     */
    public ContextSnapshot toContextSnapshot() {
        return new ContextSnapshot(
                exceptionType,
                rootCauseType,
                rootCauseMessage != null ? rootCauseMessage : "",
                stackTrace,
                recentLogs,
                springVersion,
                javaVersion,
                activeProfiles,
                packaging,
                deploymentEnvironment,
                database,
                webStack,
                buildTool
        );
    }
}