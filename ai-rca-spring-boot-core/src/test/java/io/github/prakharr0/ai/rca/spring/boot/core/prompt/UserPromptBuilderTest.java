package io.github.prakharr0.ai.rca.spring.boot.core.prompt;

import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPromptBuilderTest {

    @Test
    void promptContainsExceptionType() {
        String prompt = UserPromptBuilder.build(snapshot("com.example.FooException"));
        assertThat(prompt).contains("com.example.FooException");
    }

    @Test
    void promptContainsRootCauseMessage() {
        ContextSnapshot c = new ContextSnapshot(
                "java.lang.RuntimeException", "java.lang.NullPointerException",
                "userId is null", "at Foo.java:1", "",
                "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven"
        );
        assertThat(UserPromptBuilder.build(c)).contains("userId is null");
    }

    @Test
    void promptContainsXmlSectionTags() {
        String prompt = UserPromptBuilder.build(snapshot("java.lang.RuntimeException"));
        assertThat(prompt).contains("<EXCEPTION_SUMMARY>");
        assertThat(prompt).contains("<STACK_TRACE>");
        assertThat(prompt).contains("<LOG_CONTEXT>");
        assertThat(prompt).contains("<APPLICATION_CONTEXT>");
        assertThat(prompt).contains("<INSTRUCTIONS>");
        assertThat(prompt).contains("<OUTPUT_FORMAT>");
    }

    @Test
    void promptContainsSpringVersion() {
        ContextSnapshot c = contextWith("spring-version-test");
        String prompt = UserPromptBuilder.build(c);
        assertThat(prompt).contains("spring-version-test");
    }

    @Test
    void promptContainsActiveProfiles() {
        ContextSnapshot c = new ContextSnapshot(
                "Ex", "Ex", "msg", "stack", "logs",
                "3.2.0", "21", "prod,feature-x", "jar", "kubernetes", "jdbc", "spring-mvc", "maven"
        );
        assertThat(UserPromptBuilder.build(c)).contains("prod,feature-x");
    }

    @Test
    void stackTrace_truncatedAt2000Chars() {
        String longStack = "a".repeat(3000);
        ContextSnapshot c = new ContextSnapshot(
                "Ex", "Ex", "msg", longStack, "",
                "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven"
        );
        String prompt = UserPromptBuilder.build(c);
        assertThat(prompt).contains("[stack truncated]");
        assertThat(prompt).doesNotContain("a".repeat(2001));
    }

    @Test
    void logs_truncatedAt2000Chars() {
        String longLogs = "b".repeat(3000);
        ContextSnapshot c = new ContextSnapshot(
                "Ex", "Ex", "msg", "stack", longLogs,
                "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven"
        );
        String prompt = UserPromptBuilder.build(c);
        assertThat(prompt).contains("[... older log lines omitted]");
    }

    @Test
    void nullStackTrace_rendersNa() {
        ContextSnapshot c = new ContextSnapshot(
                "Ex", "Ex", "msg", null, null,
                "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven"
        );
        assertThat(UserPromptBuilder.build(c)).contains("N/A");
    }

    @Test
    void promptInstructsThreePhaseAnalysis() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"));
        assertThat(prompt).contains("PHASE 1");
        assertThat(prompt).contains("PHASE 2");
        assertThat(prompt).contains("PHASE 3");
    }

    @Test
    void promptInstructsNoFixes() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"));
        assertThat(prompt).doesNotContain("Do NOT suggest fixes");
    }

    // ── RAG overload ──────────────────────────────────────────────────────────

    @Test
    void ragOverload_withNullBlocks_matchesSingleArgOverload() {
        ContextSnapshot c = snapshot("java.lang.RuntimeException");
        assertThat(UserPromptBuilder.build(c, null, null))
                .isEqualTo(UserPromptBuilder.build(c));
    }

    @Test
    void ragOverload_injectsSimilarIncidentsSection() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"), "incident data here", null);
        assertThat(prompt).contains("<SIMILAR_PAST_INCIDENTS>");
        assertThat(prompt).contains("incident data here");
        assertThat(prompt).contains("</SIMILAR_PAST_INCIDENTS>");
    }

    @Test
    void ragOverload_injectsRunbookSection() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"), null, "runbook content here");
        assertThat(prompt).contains("<RELEVANT_RUNBOOK_SECTIONS>");
        assertThat(prompt).contains("runbook content here");
        assertThat(prompt).contains("</RELEVANT_RUNBOOK_SECTIONS>");
    }

    @Test
    void ragOverload_injectsBothSections() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"), "incidents", "runbook");
        assertThat(prompt).contains("<SIMILAR_PAST_INCIDENTS>");
        assertThat(prompt).contains("<RELEVANT_RUNBOOK_SECTIONS>");
    }

    @Test
    void ragOverload_ragSectionsAppearBeforeInstructions() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"), "incidents", "runbook");
        int ragIndex         = prompt.indexOf("<SIMILAR_PAST_INCIDENTS>");
        int instructionIndex = prompt.indexOf("<INSTRUCTIONS>");
        assertThat(ragIndex).isLessThan(instructionIndex);
    }

    @Test
    void ragOverload_blankSimilarIncidents_notInjected() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"), "   ", null);
        assertThat(prompt).doesNotContain("<SIMILAR_PAST_INCIDENTS>");
    }

    @Test
    void ragOverload_blankRunbook_notInjected() {
        String prompt = UserPromptBuilder.build(snapshot("Ex"), null, "   ");
        assertThat(prompt).doesNotContain("<RELEVANT_RUNBOOK_SECTIONS>");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ContextSnapshot snapshot(String exceptionType) {
        return new ContextSnapshot(exceptionType, exceptionType, "msg",
                "at Foo.java:1", "log line",
                "3.2.0", "21", "default", "jar", "local", "none", "spring-mvc", "maven");
    }

    private ContextSnapshot contextWith(String springVersion) {
        return new ContextSnapshot("Ex", "Ex", "msg", "stack", "logs",
                springVersion, "21", "default", "jar", "local", "none", "spring-mvc", "maven");
    }
}