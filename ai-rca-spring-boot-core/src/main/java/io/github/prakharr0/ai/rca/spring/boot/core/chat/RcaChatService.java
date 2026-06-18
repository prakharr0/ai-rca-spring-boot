package io.github.prakharr0.ai.rca.spring.boot.core.chat;

import io.github.prakharr0.ai.rca.spring.boot.core.model.chat.ChatAnswer;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import io.github.prakharr0.ai.rca.spring.boot.core.util.RcaTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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

    private static final Logger log = LoggerFactory.getLogger(RcaChatService.class);

    private static final Pattern NATURAL_TIME_PATTERN = Pattern.compile(
            "(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM|am|pm)\\s+on\\s+\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})"
    );

    private static final String CHAT_SYSTEM_PROMPT = """
            You are an AI-powered RCA (Root Cause Analysis) assistant for Spring Boot production incidents.

            You receive structured JSON event context. Each object is an ExceptionOccurrence with:
              - eventId, occurredAt, exceptionType, rootCauseType, exceptionMessage
              - httpMethod, requestPath, threadName
              - analysisStatus: COMPLETED | PENDING | FAILED
              - analysis (only present when COMPLETED):
                  - analysisConfidence: float 0.0–1.0
                  - knownPattern: named failure pattern (never null)
                  - rootCauses[]: ranked hypotheses, each with:
                      rank (1 = most likely), title, likelihood (High/Medium/Low),
                      category (Configuration|Code|Infrastructure|Dependency|Environment),
                      reasoning (2 sentences), diagnosticStep (1 sentence), estimatedTimeToVerify
                  - missingInformation[]: what would raise confidence (empty if confidence >= 0.6)

            Rules for answering:
            1. For "why did X happen" or root cause questions:
               - State the rank-1 root cause title and its likelihood first.
               - Give a one-sentence summary of the reasoning.
               - List remaining ranked causes briefly if there are more than one.
               - State the analysis confidence as a percentage (e.g. "Confidence: 82%").
               - State the knownPattern if present.
               - End with the top diagnosticStep as the recommended next action.
            2. For timeline or count questions: summarize from occurredAt timestamps.
            3. If analysisStatus is PENDING or FAILED, say so — do not invent a root cause.
            4. If missingInformation is non-empty, mention what is missing and why it matters.
            5. Use only the data provided — do not hallucinate or infer beyond the JSON.
            6. If no events match the question, say so clearly.

            Formatting rules:
            - No markdown tables or pipe-separated lines.
            - Use bold headings for sections: **Root Cause**, **Confidence**, **Pattern**, **Next Step**.
            - Put each key fact on its own line.
            - Be factual and direct — no filler phrases.
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
            return new ChatAnswer("Ask a question about exception timeline or RCA output.", List.of(), null, null, null);
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
                        resolvedTime,
                        null,
                        null
                );
            }
            context = List.of(nearest);
        } else {
            context = timelineStore.latest(defaultContextEvents);
            if (context.isEmpty()) {
                return new ChatAnswer("No exception events are available yet.", List.of(), null, null, null);
            }
        }

        String promptContext;
        try {
            promptContext = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        } catch (Exception e) {
            promptContext = "[]";
        }

        ChatResponse chatResponse = chatClient.prompt()
                .system(CHAT_SYSTEM_PROMPT)
                .user("""
                        Question: %s

                        Event context (JSON):
                        %s
                        """.formatted(question, promptContext))
                .call()
                .chatResponse();

        String response;
        if (chatResponse == null || chatResponse.getResult() == null) {
            response = "No response was generated for this question.";
        } else {
            response = chatResponse.getResult().getOutput().getText();
            if (response == null || response.isBlank()) {
                response = "No response was generated for this question.";
            }
        }
        response = normalizeFormatting(response);

        Long inputTokens = null;
        Long outputTokens = null;
        if (chatResponse != null) {
            try {
                Usage usage = chatResponse.getMetadata().getUsage();
                Integer prompt = usage.getPromptTokens();
                Integer completion = usage.getCompletionTokens();
                if (prompt != null) inputTokens = prompt.longValue();
                if (completion != null) outputTokens = completion.longValue();
            } catch (Exception e) {
                log.debug("[AI-RCA] Could not extract token usage from chat response: {}", e.getMessage());
            }
        }

        List<String> referencedIds = new ArrayList<>();
        for (ExceptionOccurrence occurrence : context) {
            referencedIds.add(occurrence.getEventId());
        }

        return new ChatAnswer(response.trim(), referencedIds, resolvedTime, inputTokens, outputTokens);
    }

    private String normalizeFormatting(String response) {
        String[] lines = response.split("\\R");
        StringBuilder normalized = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                normalized.append('\n');
                continue;
            }

            if (isTableSeparator(line)) {
                continue;
            }

            if (line.startsWith("|") && line.endsWith("|")) {
                String converted = convertTableLineToText(line);
                if (!converted.isBlank()) {
                    normalized.append(converted).append('\n');
                }
                continue;
            }

            normalized.append(rawLine).append('\n');
        }

        return normalized.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean isTableSeparator(String line) {
        return line.matches("^\\|?[\\s:-]+(\\|[\\s:-]+)+\\|?$");
    }

    private String convertTableLineToText(String line) {
        String content = line.substring(1, line.length() - 1).trim();
        if (content.isBlank()) {
            return "";
        }

        String[] cells = content.split("\\|");
        List<String> values = new ArrayList<>();
        for (String cell : cells) {
            String value = cell.trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return String.join(" - ", values);
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
