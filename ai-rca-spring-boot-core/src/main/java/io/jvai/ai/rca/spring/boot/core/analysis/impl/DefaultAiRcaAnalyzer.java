package io.jvai.ai.rca.spring.boot.core.analysis.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.scheduling.annotation.Async;
import io.jvai.ai.rca.spring.boot.core.analysis.AiRcaAnalyzer;
import io.jvai.ai.rca.spring.boot.core.context.ContextCollector;
import io.jvai.ai.rca.spring.boot.core.context.ContextSnapshot;
import io.jvai.ai.rca.spring.boot.core.context.ExceptionFingerprint;
import io.jvai.ai.rca.spring.boot.core.model.AiRcaResponse;
import io.jvai.ai.rca.spring.boot.core.prompt.SystemPrompts;
import io.jvai.ai.rca.spring.boot.core.prompt.UserPromptBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class DefaultAiRcaAnalyzer implements AiRcaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiRcaAnalyzer.class);

    private final ChatClient chatClient;
    private final ContextCollector collector;
    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Async
    @Override
    public void analyze(Throwable throwable) {

        ContextSnapshot snapshot = collector.collect(throwable);
        String fingerprint = ExceptionFingerprint.generate(snapshot);

        BeanOutputConverter<AiRcaResponse> converter = new BeanOutputConverter<>(AiRcaResponse.class);

        String response;
        if (cache.containsKey(fingerprint)) {
            response = cache.get(fingerprint);
        } else {
            response = chatClient.prompt()
                    .system(SystemPrompts.SYSTEM_PROMPT)
                    .user(UserPromptBuilder.build(snapshot))
                    .call()
                    .content();

            response = stripMarkdown(response);
        }

        if (response == null) {
            log.warn("AI Analysis Failed for {}", throwable.getMessage(), throwable.getCause());
            return;
        }

        cache.put(fingerprint, response);

        log.warn("[AI-RCA-SPRING-BOOT-STARTER] Analysis results for: {}\n{}", throwable.getLocalizedMessage(), response);
    }

    public Map<String, String> getResults() {
        return cache;
    }

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