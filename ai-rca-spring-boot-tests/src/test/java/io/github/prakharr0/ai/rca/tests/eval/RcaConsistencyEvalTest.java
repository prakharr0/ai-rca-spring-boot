package io.github.prakharr0.ai.rca.tests.eval;

import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.github.prakharr0.ai.rca.spring.boot.core.model.RootCause;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.SystemPrompts;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.UserPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level 2 eval — consistency (rank stability across repeated runs).
 *
 * <p><b>What it tests:</b> For every fixture, the model is called {@value #REPS} times at
 * production temperature. The test asserts:
 * <ul>
 *   <li>Rank-1 title varies by at most 2 distinct values across all runs (rephrasing is fine,
 *       category-flipping is not)</li>
 *   <li>Rank-1 category is unanimous across all runs</li>
 *   <li>Confidence variance (std dev) stays below {@value #MAX_CONFIDENCE_STD_DEV}</li>
 * </ul>
 *
 * <p><b>Why @Disabled:</b> 13 fixtures × 5 reps = 65 API calls. Run manually before/after
 * prompt changes to compare stability metrics in the log output.
 *
 * <p><b>When to fail the category assertion vs. just log it:</b> A category flip on rank-1
 * (e.g. "Code" → "Infrastructure" for an NPE) means the model can't reliably classify the
 * exception. That's a prompt quality problem, not just variance — hard-fail it.
 */
@Disabled("Requires live API key — 65 API calls per run. Run manually before/after prompt changes.")
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class RcaConsistencyEvalTest {

    private static final int REPS = 5;
    private static final double EVAL_TEMPERATURE = 0.1;

    /**
     * Allow up to 2 distinct rank-1 titles across {@value #REPS} runs.
     * Rephrasing the same idea produces distinct strings — this threshold allows for that
     * while catching genuine category-level flips.
     */
    private static final int MAX_UNIQUE_RANK1_TITLES = 2;

    /**
     * Standard deviation of confidence scores across runs must stay below this value.
     * Higher variance at low temperature signals the prompt is underspecified for this
     * exception type.
     */
    private static final double MAX_CONFIDENCE_STD_DEV = 0.15;

    @Autowired
    @Qualifier("rcaAnalyzerChatClient")
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    static Stream<org.junit.jupiter.params.provider.Arguments> fixtures() {
        return FixtureLoader.fixtureArguments();
    }

    @ParameterizedTest(name = "[consistency] {0}")
    @MethodSource("fixtures")
    void givenFixture_atProductionTemperature_thenRank1IsStable(String fixtureId, EvalFixture fixture) {
        log.info("[Consistency Eval] Running fixture: {} ({} reps at temp={})",
                fixtureId, REPS, EVAL_TEMPERATURE);

        List<AiRcaResponse> responses = new ArrayList<>();
        for (int i = 0; i < REPS; i++) {
            AiRcaResponse response = callAndParse(fixture, i);
            responses.add(response);
        }

        logConsistencySummary(fixtureId, fixture, responses);
        assertConsistency(fixtureId, fixture, responses);
    }

    private void assertConsistency(String fixtureId, EvalFixture fixture, List<AiRcaResponse> responses) {
        // All responses must have at least one root cause
        assertThat(responses)
                .as("All runs must return at least one root cause for fixture: %s", fixtureId)
                .allMatch(r -> r.rootCauses() != null && !r.rootCauses().isEmpty());

        // Rank-1 title uniqueness — rephrasing ok, category-flip not
        long uniqueRank1Titles = responses.stream()
                .map(r -> r.rootCauses().getFirst().title().toLowerCase().trim())
                .distinct()
                .count();

        assertThat(uniqueRank1Titles)
                .as("Rank-1 title has %d unique values across %d runs (max allowed: %d) for fixture: %s",
                        uniqueRank1Titles, REPS, MAX_UNIQUE_RANK1_TITLES, fixtureId)
                .isLessThanOrEqualTo(MAX_UNIQUE_RANK1_TITLES);

        // Rank-1 category must be unanimous — this is the hard constraint
        long uniqueRank1Categories = responses.stream()
                .map(r -> r.rootCauses().getFirst().category())
                .distinct()
                .count();

        assertThat(uniqueRank1Categories)
                .as("Rank-1 category flipped across runs for fixture: %s (expected: %s). "
                                + "This indicates the prompt doesn't distinguish this exception type reliably.",
                        fixtureId, fixture.expectedCategory())
                .isEqualTo(1);

        // Confidence standard deviation
        double avgConfidence = responses.stream()
                .mapToDouble(AiRcaResponse::analysisConfidence).average().orElse(0.0);
        double stdDev = Math.sqrt(responses.stream()
                .mapToDouble(r -> Math.pow(r.analysisConfidence() - avgConfidence, 2))
                .average().orElse(0.0));

        assertThat(stdDev)
                .as("Confidence std dev %.4f exceeds %.4f for fixture: %s — temperature may be too high",
                        stdDev, MAX_CONFIDENCE_STD_DEV, fixtureId)
                .isLessThanOrEqualTo(MAX_CONFIDENCE_STD_DEV);
    }

    private void logConsistencySummary(String fixtureId, EvalFixture fixture, List<AiRcaResponse> responses) {
        double avg = responses.stream().mapToDouble(AiRcaResponse::analysisConfidence).average().orElse(0);
        double min = responses.stream().mapToDouble(AiRcaResponse::analysisConfidence).min().orElse(0);
        double max = responses.stream().mapToDouble(AiRcaResponse::analysisConfidence).max().orElse(0);
        double stdDev = Math.sqrt(responses.stream()
                .mapToDouble(r -> Math.pow(r.analysisConfidence() - avg, 2)).average().orElse(0));

        long uniqueTitles = responses.stream()
                .map(r -> r.rootCauses().getFirst().title().toLowerCase())
                .distinct().count();

        Map<String, Long> categoryDist = responses.stream()
                .collect(Collectors.groupingBy(
                        r -> r.rootCauses().getFirst().category(), Collectors.counting()));

        log.info("=== Consistency: {} | {} reps | temp={} ===", fixtureId, REPS, EVAL_TEMPERATURE);
        log.info("  expected category: {}", fixture.expectedCategory());
        log.info("  confidence avg={:.3f} min={:.3f} max={:.3f} stdDev={:.4f}", avg, min, max, stdDev);
        log.info("  rank-1 title uniqueness: {}/{} (1 = perfectly stable)", uniqueTitles, REPS);
        log.info("  rank-1 category distribution: {}", categoryDist);

        responses.forEach(r -> {
            RootCause rank1 = r.rootCauses().getFirst();
            log.info("  run #{} confidence={} rank1=[{}] category={}",
                    responses.indexOf(r), r.analysisConfidence(), rank1.title(), rank1.category());
        });
    }

    private AiRcaResponse callAndParse(EvalFixture fixture, int runIndex) {
        log.info("[Consistency Eval] fixture={} run={}", fixture.id(), runIndex);

        ChatResponse chatResponse = chatClient.prompt()
                .system(SystemPrompts.SYSTEM_PROMPT)
                .user(UserPromptBuilder.build(fixture.toContextSnapshot()))
                .options(ChatOptions.builder().temperature(EVAL_TEMPERATURE).build())
                .call()
                .chatResponse();

        assertThat(chatResponse).as("ChatResponse must not be null on run %d", runIndex).isNotNull();
        assertThat(chatResponse.getResult()).as("Result must not be null on run %d", runIndex).isNotNull();

        String content = chatResponse.getResult().getOutput().getText();
        if (content != null) {
            content = content.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "");
        }

        try {
            return objectMapper.readValue(content, AiRcaResponse.class);
        } catch (Exception e) {
            throw new AssertionError(
                    "JSON parse failed on run " + runIndex + " for fixture " + fixture.id()
                            + ". Raw response:\n" + content, e);
        }
    }
}