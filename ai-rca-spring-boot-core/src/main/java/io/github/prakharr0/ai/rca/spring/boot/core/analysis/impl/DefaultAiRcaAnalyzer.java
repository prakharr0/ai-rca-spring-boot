package io.github.prakharr0.ai.rca.spring.boot.core.analysis.impl;

import io.github.prakharr0.ai.rca.spring.boot.core.analysis.LowConfidenceAction;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AnalysisMetadata;
import io.github.prakharr0.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.scheduling.annotation.Async;
import io.github.prakharr0.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextCollector;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.github.prakharr0.ai.rca.spring.boot.core.context.ExceptionFingerprint;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.SystemPrompts;
import io.github.prakharr0.ai.rca.spring.boot.core.prompt.UserPromptBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link AiRcaAnalyzer} that performs AI-powered
 * Root Cause Analysis (RCA) for thrown exceptions in a Spring Boot application.
 *
 * <p>
 * This analyzer:
 * <ul>
 *     <li>Collects contextual information about a {@link Throwable} using {@link ContextCollector}</li>
 *     <li>Generates a deterministic fingerprint via {@link ExceptionFingerprint}</li>
 *     <li>Invokes a Spring AI {@link ChatClient} with system and user prompts</li>
 *     <li>Caches responses to avoid duplicate AI calls for identical exception fingerprints</li>
 *     <li>Logs the AI-generated analysis output</li>
 * </ul>
 *
 * <h2>Execution Model</h2>
 * The {@link #analyze(Throwable)} method is annotated with {@link org.springframework.scheduling.annotation.Async},
 * meaning analysis runs asynchronously and does not block the calling thread.
 *
 * <h2>Caching Strategy</h2>
 * <p>
 * A {@link ConcurrentHashMap} is used to cache AI responses keyed by an exception fingerprint.
 * If the same exception (based on contextual fingerprint) occurs again,
 * the cached result is reused instead of invoking the AI model.
 *
 * <h2>AI Interaction Flow</h2>
 * <ol>
 *     <li>Collect {@link ContextSnapshot}</li>
 *     <li>Generate fingerprint</li>
 *     <li>Build prompts using {@link SystemPrompts} and {@link UserPromptBuilder}</li>
 *     <li>Call the AI model via {@link ChatClient}</li>
 *     <li>Strip Markdown formatting from response</li>
 *     <li>Cache and log the result</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * The internal cache is thread-safe. The class itself is stateless except for
 * the cache and injected collaborators, making it safe for singleton Spring usage.
 *
 * <h2>Logging</h2>
 * Results are logged using SLF4J at WARN level with the prefix:
 * <pre>
 * [AI-RCA-SPRING-BOOT-STARTER]
 * </pre>
 *
 * @see AiRcaAnalyzer
 * @see ContextCollector
 * @see ContextSnapshot
 * @see ExceptionFingerprint

 */
@RequiredArgsConstructor
public class DefaultAiRcaAnalyzer implements AiRcaAnalyzer {

    /**
     * Logger for AI RCA analysis output and failure events.
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultAiRcaAnalyzer.class);

    /**
     * Spring AI client used to communicate with the configured LLM.
     */
    private final ChatClient chatClient;
    private final ContextCollector collector;
    private final ObjectMapper objectMapper;
    private final ExceptionTimelineStore timelineStore;

    /**
     * Minimum confidence score to accept a result. 0.0 disables the threshold.
     * Configured via {@code ai.rca.min-confidence}.
     */
    private final double minConfidence;

    /**
     * What to do when confidence falls below {@link #minConfidence}.
     * Configured via {@code ai.rca.low-confidence-action}.
     */
    private final LowConfidenceAction lowConfidenceAction;

    /**
     * In-memory cache storing AI responses keyed by exception fingerprint.
     * <p>
     * Key: Exception fingerprint
     * Value: AI-generated RCA response (Markdown stripped)
     */
    private final Map<String, AiRcaResponse> cache = new ConcurrentHashMap<>();

    /**
     * Performs asynchronous AI-based root cause analysis for a given {@link Throwable}.
     *
     * <p><b>Execution Steps:</b></p>
     * <ol>
     *     <li>Collect {@link ContextSnapshot} from the exception</li>
     *     <li>Generate fingerprint using {@link ExceptionFingerprint}</li>
     *     <li>Check cache for existing analysis</li>
     *     <li>If not cached, build prompts and invoke AI model</li>
     *     <li>Strip Markdown formatting from response</li>
     *     <li>Store result in cache</li>
     *     <li>Log AI analysis output</li>
     * </ol>
     *
     * <p>
     * If AI invocation fails or returns {@code null}, a warning is logged
     * and no cache entry is stored.
     *
     * <p>
     * This method is non-blocking and executes on a Spring async executor.
     *
     * @param throwable the exception to analyze; must not be {@code null}
     */
    @Async
    @Override
    public void analyze(Throwable throwable) {
        ContextSnapshot snapshot = collector.collect(throwable);
        String fingerprint = ExceptionFingerprint.generate(snapshot);

        AiRcaResponse response;
        if (cache.containsKey(fingerprint)) {
            response = cache.get(fingerprint);
        } else {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(SystemPrompts.SYSTEM_PROMPT)
                    .user(UserPromptBuilder.build(snapshot))
                    .call()
                    .chatResponse();

            if (chatResponse == null || chatResponse.getResult() == null) {
                log.warn("AI Analysis Failed for {}", throwable.getMessage(), throwable.getCause());
                timelineStore.markFailureByFingerprint(fingerprint, "AI response was empty");
                return;
            }

            String content = stripMarkdown(chatResponse.getResult().getOutput().getText());
            AnalysisMetadata metadata = extractMetadata(chatResponse);

            try {
                response = objectMapper.readValue(content, AiRcaResponse.class)
                        .withMetadata(metadata);
            } catch (Exception e) {
                log.warn("[AI-RCA-SPRING-BOOT-STARTER] Analysis results for: {}\n{}", throwable.getLocalizedMessage(), content);
                timelineStore.markFailureByFingerprint(fingerprint, "AI response could not be parsed");
                return;
            }

            boolean isLowConfidence = minConfidence > 0.0 && response.analysisConfidence() < minConfidence;
            if (isLowConfidence) {
                log.warn("[AI-RCA] Low confidence result: score={} threshold={} fingerprint={}",
                        response.analysisConfidence(), minConfidence, fingerprint);
                if (lowConfidenceAction == LowConfidenceAction.SKIP) {
                    timelineStore.markFailureByFingerprint(fingerprint,
                            "Confidence %.2f below threshold %.2f — skipped".formatted(
                                    response.analysisConfidence(), minConfidence));
                    return;
                }
            }
            response = response.withLowConfidenceFlag(isLowConfidence);
        }

        AiRcaResponse finalResponse = response;
        cache.computeIfAbsent(fingerprint, k -> finalResponse);
        timelineStore.attachAnalysisByFingerprint(fingerprint, response);
        log.warn("[AI-RCA-SPRING-BOOT-STARTER] Analysis results for: {}\n{}", throwable.getLocalizedMessage(),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

    /**
     * Returns all cached AI analysis results.
     *
     * <p>
     * The returned map is the live internal cache. Modifying it will
     * affect the analyzer state.
     *
     * @return a thread-safe map of exception fingerprint to AI response
     */
    public Map<String, AiRcaResponse> getResults() {
        return cache;
    }

    /**
     * Extracts token usage from the provider's {@link ChatResponse} metadata.
     *
     * <p>Token counts are reported by the AI provider and reflect actual API usage for this call.
     * Both values are nullable — providers may omit usage data on error responses.
     *
     * <p>If {@code outputTokens} is consistently near the configured {@code max_tokens} limit,
     * the response is likely being truncated — lower temperature or reduce prompt size.
     */
    private AnalysisMetadata extractMetadata(ChatResponse chatResponse) {
        try {
            Usage usage = chatResponse.getMetadata().getUsage();
            Integer prompt = usage.getPromptTokens();
            Integer completion = usage.getCompletionTokens();
            Integer total = usage.getTotalTokens();
            return new AnalysisMetadata(
                    prompt.longValue(),
                    completion.longValue(),
                    total.longValue()
            );
        } catch (Exception e) {
            log.debug("[AI-RCA] Could not extract token usage from ChatResponse metadata: {}", e.getMessage());
            return new AnalysisMetadata(null, null, null);
        }
    }

    /**
     * Removes Markdown code block formatting from AI responses.
     *
     * <p>
     * Specifically removes:
     * <ul>
     *     <li>{@code ```json ... ```}</li>
     *     <li>{@code ``` ... ```}</li>
     * </ul>
     *
     * This ensures cleaner log output and easier structured parsing.
     *
     * @param content raw AI response content
     * @return cleaned response without Markdown wrappers, or {@code null} if input is null
     */
    private String stripMarkdown(String content) {
        if (content == null) return null;

        String stripped = content.trim();

        // Remove ```json ... ``` or ``` ... ```
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceAll("^```(?:json)?\\s*", "");
            stripped = stripped.replaceAll("\\s*```$", "");
        }

        return stripped.trim();
    }
}
