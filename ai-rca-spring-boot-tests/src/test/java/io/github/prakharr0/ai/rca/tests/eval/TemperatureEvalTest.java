package io.github.prakharr0.ai.rca.tests.eval;

import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.SystemPrompts;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.UserPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class TemperatureEvalTest {

    private static final int REPS = 10;

    @Autowired
    private ContextCollector collector;

    @Autowired
    @Qualifier("rcaAnalyzerChatClient")
    private ChatClient rcaChatClient;

    private Exception exception;

    @BeforeEach
    void init() throws IOException {
        final byte[] bytes = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("throwable.txt")).readAllBytes();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            exception = (Exception) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 0.3, 0.7, 1.0})
    void givenTemperatures_whenAnalysisTriggered_thenOutputEvaluated(double temperature) {

        final ContextSnapshot snapshot = collector.collect(exception);
        final String userPrompt = UserPromptBuilder.build(snapshot);
        List<AiRcaResponse> responses = new ArrayList<>();

        for (int i = 0; i < REPS; i++) {

            log.info("[Temperature Evaluation Test]: Iteration #{}", i);

            final ChatResponse chatResponse = rcaChatClient.prompt()
                            .system(SystemPrompts.SYSTEM_PROMPT)
                            .user(userPrompt)
                            .options(ChatOptions.builder().temperature(temperature).build())
                            .call()
                            .chatResponse();

            assertNotNull(chatResponse);
            assertNotNull(chatResponse.getResult());

            final String content = chatResponse.getResult().getOutput().getText();
            final AiRcaResponse res = new ObjectMapper()
                    .readValue(content, AiRcaResponse.class);
            responses.add(res);
            log.info("[Temperature Evaluation Test]: Response: {}\n\n", res);
        }

        logEvalSummary(temperature, responses);

        assertThat(responses).hasSize(REPS);
        assertThat(responses).allMatch(r -> r.rootCauses() != null && !r.rootCauses().isEmpty());
        assertThat(responses).allMatch(r -> r.analysisConfidence() >= 0.0 && r.analysisConfidence() <= 1.0);
    }

    private void logEvalSummary(double temperature, List<AiRcaResponse> responses) {
        double avgConfidence = responses.stream().mapToDouble(AiRcaResponse::analysisConfidence).average().orElse(0.0);
        double minConfidence = responses.stream().mapToDouble(AiRcaResponse::analysisConfidence).min().orElse(0.0);
        double maxConfidence = responses.stream().mapToDouble(AiRcaResponse::analysisConfidence).max().orElse(0.0);
        double stdDev = Math.sqrt(responses.stream()
                .mapToDouble(r -> Math.pow(r.analysisConfidence() - avgConfidence, 2))
                .average().orElse(0.0));

        // Semantic consistency: compare rank-1 titles, not full reasoning text
        long uniqueRank1Titles = responses.stream()
                .map(r -> r.rootCauses().getFirst().title())
                .distinct()
                .count();

        // Structural consistency: rank-3 should always be Low likelihood
        long rank3LikelihoodViolations = responses.stream()
                .filter(r -> !"Low".equalsIgnoreCase(r.rootCauses().get(2).likelihood()))
                .count();

        // Category distribution for rank-1 (should be Code for this exception)
        Map<String, Long> rank1Categories = responses.stream()
                .collect(Collectors.groupingBy(
                        r -> r.rootCauses().getFirst().category(),
                        Collectors.counting()));

        log.info("=== Temperature {} | {} reps ===", temperature, REPS);
        log.info("  confidence  avg={}  min={}  max={}  stdDev={}", avgConfidence, minConfidence, maxConfidence, String.format("%.4f", stdDev));
        log.info("  rank-1 title uniqueness: {}/{} (lower = more consistent)", uniqueRank1Titles, REPS);
        log.info("  rank-3 likelihood violations (expected Low): {}/{}", rank3LikelihoodViolations, REPS);
        log.info("  rank-1 category distribution: {}", rank1Categories);
        responses.forEach(r -> log.info("  [{}] confidence={}  rank1={}  rank3likelihood={}",
                responses.indexOf(r), r.analysisConfidence(),
                r.rootCauses().getFirst().title(), r.rootCauses().get(2).likelihood()));
    }
}