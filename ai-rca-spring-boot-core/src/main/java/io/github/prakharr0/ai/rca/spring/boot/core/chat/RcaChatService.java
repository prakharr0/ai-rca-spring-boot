package io.github.prakharr0.ai.rca.spring.boot.core.chat;

import io.github.prakharr0.ai.rca.spring.boot.core.model.chat.ChatAnswer;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import io.github.prakharr0.ai.rca.spring.boot.core.util.RcaTimeParser;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RcaChatService {

    private static final Pattern NATURAL_TIME_PATTERN = Pattern.compile(
            "(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM|am|pm)\\s+on\\s+\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})"
    );

    private static final String CHAT_SYSTEM_PROMPT = """
            You are an RCA assistant for Spring Boot production incidents.
            Use only the provided event timeline and analysis output.
            If data is insufficient, explicitly say what is missing.
            When asked "why" for an event, summarize the ranked root causes and confidence.
            Keep the answer concise and factual.
            """;

    private final ChatClient chatClient;
    private final ExceptionTimelineStore timelineStore;
    private final ObjectMapper objectMapper;
    private final int defaultToleranceSeconds;
    private final int defaultContextEvents;

    public RcaChatService(
            ChatClient chatClient,
            ExceptionTimelineStore timelineStore,
            ObjectMapper objectMapper,
            int defaultToleranceSeconds,
            int defaultContextEvents
    ) {
        this.chatClient = chatClient;
        this.timelineStore = timelineStore;
        this.objectMapper = objectMapper;
        this.defaultToleranceSeconds = defaultToleranceSeconds;
        this.defaultContextEvents = defaultContextEvents;
    }

    public ChatAnswer chat(String question, Integer toleranceSeconds, ZoneId zoneId) {
        if (question == null || question.isBlank()) {
            return new ChatAnswer("Ask a question about exception timeline or RCA output.", List.of(), null);
        }

        Instant resolvedTime = resolveTimeFromQuestion(question, zoneId).orElse(null);

        List<ExceptionOccurrence> context;
        if (resolvedTime != null) {
            Duration tolerance = Duration.ofSeconds(Math.max(60, toleranceSeconds == null ? defaultToleranceSeconds : toleranceSeconds));
            ExceptionOccurrence nearest = timelineStore.findAt(resolvedTime, tolerance);
            if (nearest == null) {
                return new ChatAnswer(
                        "No exception event was found near %s (tolerance %d seconds).".formatted(resolvedTime, tolerance.toSeconds()),
                        List.of(),
                        resolvedTime
                );
            }
            context = List.of(nearest);
        } else {
            context = timelineStore.latest(defaultContextEvents);
            if (context.isEmpty()) {
                return new ChatAnswer("No exception events are available yet.", List.of(), null);
            }
        }

        String promptContext;
        try {
            promptContext = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        } catch (Exception e) {
            promptContext = "[]";
        }

        String response = chatClient.prompt()
                .system(CHAT_SYSTEM_PROMPT)
                .user("""
                        User question:
                        %s
                        
                        Event context JSON:
                        %s
                        """.formatted(question, promptContext))
                .call()
                .content();

        if (response == null || response.isBlank()) {
            response = "No response was generated for this question.";
        }

        List<String> referencedIds = new ArrayList<>();
        for (ExceptionOccurrence occurrence : context) {
            referencedIds.add(occurrence.getEventId());
        }

        return new ChatAnswer(response.trim(), referencedIds, resolvedTime);
    }

    private Optional<Instant> resolveTimeFromQuestion(String question, ZoneId zoneId) {
        Optional<Instant> fullMatch = RcaTimeParser.parseInstant(question, zoneId);
        if (fullMatch.isPresent()) {
            return fullMatch;
        }

        Matcher matcher = NATURAL_TIME_PATTERN.matcher(question);
        if (matcher.find()) {
            return RcaTimeParser.parseInstant(matcher.group(1), zoneId);
        }

        return Optional.empty();
    }
}
