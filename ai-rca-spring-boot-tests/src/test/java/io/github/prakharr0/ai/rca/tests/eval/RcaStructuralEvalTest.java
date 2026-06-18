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

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Level 1 eval — structural (schema) validation.
 *
 * <p><b>What it tests:</b> For every fixture, the AI must return output that:
 * <ul>
 *   <li>Parses as valid JSON without exception</li>
 *   <li>Contains all required fields</li>
 *   <li>Has enum fields within the allowed set</li>
 *   <li>Has {@code rank} values starting at 1 and incrementing by 1</li>
 *   <li>Has {@code analysisConfidence} between 0.0 and 1.0</li>
 * </ul>
 *
 * <p><b>Why @Disabled:</b> Requires a live AI API key. Run manually before any prompt change
 * to establish a baseline, and after to verify the change didn't break the contract.
 *
 * <p><b>Temperature:</b> Fixed at 0.1 — the production default. Testing at higher temperatures
 * here would conflate temperature variance with prompt regression.
 *
 * <p><b>Runs once per fixture:</b> Structural evals don't need repetition — a single parse
 * failure is enough signal. Use {@link RcaConsistencyEvalTest} if you want stability across runs.
 */
@Disabled("Requires live API key — run manually: right-click test class → Run")
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class RcaStructuralEvalTest {

    private static final double EVAL_TEMPERATURE = 0.1;

    private static final List<String> VALID_LIKELIHOODS = List.of("High", "Medium", "Low");
    private static final List<String> VALID_CATEGORIES = List.of(
            "Configuration", "Code", "Infrastructure", "Dependency", "Environment"
    );

    @Autowired
    @Qualifier("rcaAnalyzerChatClient")
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    static Stream<org.junit.jupiter.params.provider.Arguments> fixtures() {
        return FixtureLoader.fixtureArguments();
    }

    /**
     * For each fixture: call the model once and assert the response is schema-valid.
     *
     * <p>The test name in the report shows the fixture ID so failures are immediately locatable.
     */
    @ParameterizedTest(name = "[structural] {0}")
    @MethodSource("fixtures")
    void givenFixture_whenAnalyzed_thenResponseIsSchemaValid(String fixtureId, EvalFixture fixture) {
        log.info("[Structural Eval] Running fixture: {}", fixtureId);

        String rawResponse = callModel(fixture);
        log.info("[Structural Eval] Raw response for {}: {}", fixtureId, rawResponse);

        // Level 1a — must parse without exception
        AiRcaResponse response = assertDoesNotThrow(
                () -> objectMapper.readValue(rawResponse, AiRcaResponse.class),
                "JSON parse failed for fixture: " + fixtureId
        );

        // Level 1b — confidence is a valid score
        assertThat(response.analysisConfidence())
                .as("analysisConfidence must be 0.0–1.0 for fixture: %s", fixtureId)
                .isBetween(0.0, 1.0);

        // Level 1c — knownPattern must never be null or blank
        assertThat(response.knownPattern())
                .as("knownPattern must not be null or blank for fixture: %s", fixtureId)
                .isNotNull()
                .isNotBlank();

        // Level 1d — rootCauses must be present and non-empty
        assertThat(response.rootCauses())
                .as("rootCauses must not be null or empty for fixture: %s", fixtureId)
                .isNotNull()
                .isNotEmpty();

        // Level 1e — each root cause must have valid enum values and sequential ranks
        List<RootCause> rootCauses = response.rootCauses();
        for (int i = 0; i < rootCauses.size(); i++) {
            RootCause rc = rootCauses.get(i);
            int expectedRank = i + 1;

            assertThat(rc.rank())
                    .as("rootCauses[%d].rank must be %d for fixture: %s", i, expectedRank, fixtureId)
                    .isEqualTo(expectedRank);

            assertThat(rc.likelihood())
                    .as("rootCauses[%d].likelihood must be High/Medium/Low for fixture: %s", i, fixtureId)
                    .isIn(VALID_LIKELIHOODS);

            assertThat(rc.category())
                    .as("rootCauses[%d].category must be a valid category for fixture: %s", i, fixtureId)
                    .isIn(VALID_CATEGORIES);

            assertThat(rc.title())
                    .as("rootCauses[%d].title must not be blank for fixture: %s", i, fixtureId)
                    .isNotBlank();

            assertThat(rc.diagnosticStep())
                    .as("rootCauses[%d].diagnosticStep must not be blank for fixture: %s", i, fixtureId)
                    .isNotBlank();
        }

        log.info("[Structural Eval] PASSED fixture={} confidence={} pattern='{}' rootCauses={}",
                fixtureId, response.analysisConfidence(), response.knownPattern(), rootCauses.size());
    }

    private String callModel(EvalFixture fixture) {
        ChatResponse chatResponse = chatClient.prompt()
                .system(SystemPrompts.SYSTEM_PROMPT)
                .user(UserPromptBuilder.build(fixture.toContextSnapshot()))
                .options(ChatOptions.builder().temperature(EVAL_TEMPERATURE).build())
                .call()
                .chatResponse();

        assertThat(chatResponse).isNotNull();
        assertThat(chatResponse.getResult()).isNotNull();

        String content = chatResponse.getResult().getOutput().getText();
        // Strip markdown code fences if the model wraps output despite instructions
        if (content != null) {
            content = content.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "");
        }
        return content;
    }
}